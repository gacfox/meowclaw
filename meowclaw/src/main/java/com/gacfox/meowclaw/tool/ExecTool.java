package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class ExecTool {
    private static final int MAX_OUTPUT_LENGTH = 10000;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");
    private static volatile Charset cachedWindowsCharset;

    private static final ExecutorService OUTPUT_READER = Executors.newCachedThreadPool();

    @AgenticTool(name = "exec", description = "执行系统shell命令并返回输出")
    public String exec(@AgenticToolParam(name = "param", description = "执行参数") ExecParam param,
                       AgentContext ctx) {
        int timeout = param.getTimeout() != null ? param.getTimeout() : 30;
        try {
            ProcessBuilder pb = IS_WINDOWS
                    ? new ProcessBuilder("cmd", "/c", param.getCommand())
                    : new ProcessBuilder("sh", "-c", param.getCommand());
            pb.redirectErrorStream(true);
            Object cwdObj = ctx.getVariables().get("cwd");
            if (cwdObj instanceof String cwd && !cwd.isBlank()) {
                pb.directory(new File(cwd));
            }
            Process process = pb.start();
            Charset charset = resolveCharset();
            Future<String> outputFuture = OUTPUT_READER.submit(
                    () -> new String(process.getInputStream().readAllBytes(), charset));
            if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                outputFuture.cancel(true);
                return "Error: command timed out after " + timeout + " seconds";
            }
            String output = outputFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();
            return (exitCode != 0 ? "Exit code: " + exitCode + "\n" : "") + truncate(output);
        } catch (TimeoutException e) {
            return "Error: command timed out after " + timeout + " seconds";
        } catch (ExecutionException e) {
            return "Error: " + e.getCause().getMessage();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
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
