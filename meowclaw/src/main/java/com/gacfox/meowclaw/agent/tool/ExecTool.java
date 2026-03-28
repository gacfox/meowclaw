package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExecTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "exec";
    private static final String DESCRIPTION = "执行系统shell命令。支持Windows和Linux/macOS系统。命令将在工作目录下执行。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "要执行的shell命令"
                },
                "timeout": {
                  "type": "integer",
                  "description": "命令执行超时时间（秒），默认为30秒",
                  "default": 30
                }
              },
              "required": ["command"]
            }
            """;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public String getParameters() {
        return PARAMETERS;
    }

    @Override
    public Mono<String> execute(String params, ToolExecutionContext context) {
        return Mono.fromCallable(() -> {
            log.info("执行命令: {}", params);

            JsonNode node = OBJECT_MAPPER.readTree(params);

            String command = node.get("command").asText();
            if (command == null || command.isBlank()) {
                return "命令为空，无法执行";
            }
            int timeout = node.has("timeout") ? node.get("timeout").asInt(30) : 30;

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String[] cmdArray;

            if (isWindows) {
                cmdArray = new String[]{"cmd.exe", "/c", command};
            } else {
                cmdArray = new String[]{"/bin/sh", "-c", command};
            }

            ProcessBuilder pb = new ProcessBuilder(cmdArray);
            Path workspaceDir = context != null ? context.getWorkspaceDir() : null;
            if (workspaceDir != null) {
                Files.createDirectories(workspaceDir);
                pb.directory(workspaceDir.toFile());
            } else {
                pb.directory(new java.io.File("."));
            }
            pb.redirectErrorStream(true);

            Process process = pb.start();

            StringBuilder output = new StringBuilder();

            Charset outputCharset = resolveOutputCharset(isWindows);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), outputCharset))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return String.format("命令执行超时（%d秒）\n部分输出:\n%s", timeout, output.toString());
            }

            int exitCode = process.exitValue();

            if (exitCode != 0) {
                return String.format("命令退出码: %d\n输出:\n%s", exitCode, output.toString());
            }

            String result = output.toString();
            if (result.isEmpty()) {
                return "命令执行成功（无输出）";
            }

            if (result.length() > 8000) {
                result = result.substring(0, 8000) + "\n... (输出已截断，总长度: " + result.length() + " 字符)";
            }

            return result;

        }).onErrorResume(e -> {
            log.error("命令执行失败", e);
            return Mono.just("命令执行失败: " + e.getMessage());
        });
    }

    private Charset resolveOutputCharset(boolean isWindows) {
        if (!isWindows) {
            return Charset.defaultCharset();
        }
        Integer codePage = detectWindowsCodePage();
        if (codePage == null) {
            return Charset.defaultCharset();
        }
        if (codePage == 65001) {
            return StandardCharsets.UTF_8;
        }
        if (codePage == 936) {
            try {
                return Charset.forName("GBK");
            } catch (Exception e) {
                return Charset.defaultCharset();
            }
        }
        if (codePage == 437) {
            try {
                return Charset.forName("CP437");
            } catch (Exception e) {
                return Charset.defaultCharset();
            }
        }
        try {
            return Charset.forName("CP" + codePage);
        } catch (Exception e) {
            return Charset.defaultCharset();
        }
    }

    private Integer detectWindowsCodePage() {
        try {
            Process process = new ProcessBuilder("cmd.exe", "/c", "chcp")
                    .redirectErrorStream(true)
                    .start();
            byte[] bytes = process.getInputStream().readAllBytes();
            boolean ignored = process.waitFor(2, TimeUnit.SECONDS);
            String output = new String(bytes, StandardCharsets.ISO_8859_1);
            Matcher matcher = Pattern.compile("(\\d{3,5})").matcher(output);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
