package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.service.AgentService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Shell命令共享执行器，负责进程树生命周期、输出读取和结果截断
 */
@Component
public class ShellCommandExecutor {
    private static final int MAX_OUTPUT_LENGTH = 10000;
    private static final long TERMINATION_GRACE_MILLIS = 2000;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    private static final ExecutorService OUTPUT_READER = Executors.newCachedThreadPool();
    private static volatile Charset cachedWindowsCharset;

    private final AgentService agentService;

    @Autowired
    public ShellCommandExecutor(AgentService agentService) {
        this.agentService = agentService;
    }

    public String execute(ShellCommandParam param, AgentContext ctx, List<String> shellCommand) {
        return execute(param, ctx, shellCommand, param.getCommand());
    }

    public String execute(ShellCommandParam param, AgentContext ctx, List<String> shellCommand, String command) {
        int timeout = param.getTimeout() != null ? param.getTimeout() : 30;
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(new ArrayList<>(shellCommand));
            pb.command().add(command);
            pb.redirectErrorStream(true);
            Object cwdObj = ctx.getVariables().get("cwd");
            if (cwdObj instanceof String cwd && !cwd.isBlank()) {
                pb.directory(new File(cwd));
            }
            Object agentId = ctx.getVariables().get("agentId");
            if (agentId instanceof Long id) {
                Map<String, String> environment = agentService.getEnvironmentVariables(id);
                pb.environment().putAll(environment);
            }
            Process process = pb.start();
            Charset charset = resolveCharset();
            Future<?> outputFuture = startOutputReader(process, charset, output);
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                terminateProcessTree(process);
                awaitOutput(outputFuture);
                return appendMessage(output, "Error: command timed out after " + timeout + " seconds");
            }
            outputFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            return (exitCode != 0 ? "Exit code: " + exitCode + "\n" : "") + truncate(snapshot(output));
        } catch (TimeoutException e) {
            return appendMessage(output, "Error: command timed out after " + timeout + " seconds");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String message = cause.getMessage() != null ? cause.getMessage() : cause.getClass().getName();
            return appendMessage(output, "Error: " + message);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return appendMessage(output, "Error: " + message);
        }
    }

    private Future<?> startOutputReader(Process process, Charset charset, StringBuilder output) {
        return OUTPUT_READER.submit(() -> {
            try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), charset)) {
                char[] buffer = new char[4096];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    synchronized (output) {
                        output.append(buffer, 0, read);
                    }
                }
            }
            return null;
        });
    }

    private void awaitOutput(Future<?> outputFuture) {
        try {
            outputFuture.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            outputFuture.cancel(true);
        }
    }

    private void terminateProcessTree(Process process) throws InterruptedException {
        List<ProcessHandle> handles = processTree(process);
        for (ProcessHandle handle : handles) {
            if (handle.isAlive()) handle.destroy();
        }
        if (waitForExit(handles, TERMINATION_GRACE_MILLIS)) return;

        List<ProcessHandle> survivors = new ArrayList<>(handles.stream().filter(ProcessHandle::isAlive).toList());
        if (process.isAlive()) {
            process.descendants().filter(ProcessHandle::isAlive).forEach(survivors::add);
        }
        for (ProcessHandle handle : survivors) {
            handle.destroyForcibly();
        }
        waitForExit(survivors, 1000);
    }

    private List<ProcessHandle> processTree(Process process) {
        List<ProcessHandle> handles = new ArrayList<>();
        process.descendants().forEach(handles::add);
        handles.add(process.toHandle());
        return handles;
    }

    private boolean waitForExit(List<ProcessHandle> handles, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (handles.stream().noneMatch(ProcessHandle::isAlive)) return true;
            Thread.sleep(25);
        }
        return handles.stream().noneMatch(ProcessHandle::isAlive);
    }

    private String appendMessage(StringBuilder output, String message) {
        String text = truncate(snapshot(output));
        return text.isBlank() ? message : text + (text.endsWith("\n") ? "" : "\n") + message;
    }

    private String snapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }

    private Charset resolveCharset() {
        if (!IS_WINDOWS) {
            return StandardCharsets.UTF_8;
        }
        if (cachedWindowsCharset != null) {
            return cachedWindowsCharset;
        }
        Charset cs = detectWindowsCharset();
        cachedWindowsCharset = cs;
        return cs;
    }

    private Charset detectWindowsCharset() {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "chcp");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(5, TimeUnit.SECONDS);
            String codePage = output.replaceAll("\\D", "").trim();
            return switch (codePage) {
                case "936" -> Charset.forName("GBK");
                case "950" -> Charset.forName("Big5");
                case "65001" -> StandardCharsets.UTF_8;
                default -> Charset.defaultCharset();
            };
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    private String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= MAX_OUTPUT_LENGTH) return s;
        return s.substring(0, MAX_OUTPUT_LENGTH) + "\n... (output truncated)";
    }
}
