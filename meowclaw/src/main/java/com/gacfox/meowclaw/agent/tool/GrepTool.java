package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import com.gacfox.meowclaw.util.PathUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GrepTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "grep";
    private static final String DESCRIPTION = "在文件内容中搜索关键字，支持正则表达式。默认路径相对于智能体工作区。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "pattern": {
                  "type": "string",
                  "description": "要搜索的正则表达式模式"
                },
                "path": {
                  "type": "string",
                  "description": "要搜索的目录或文件路径（相对路径默认在工作区内）"
                },
                "-i": {
                  "type": "boolean",
                  "description": "忽略大小写搜索，默认false",
                  "default": false
                },
                "-n": {
                  "type": "boolean",
                  "description": "显示行号，默认true",
                  "default": true
                },
                "glob": {
                  "type": "string",
                  "description": "文件过滤 glob 模式，例如 *.java 或 *.ts"
                }
              },
              "required": ["pattern"]
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
            JsonNode node = OBJECT_MAPPER.readTree(params);
            String pattern = node.get("pattern").asText();
            String pathValue = node.has("path") ? node.get("path").asText() : ".";
            boolean ignoreCase = node.has("-i") && node.get("-i").asBoolean(false);
            boolean showLineNumber = !node.has("-n") || node.get("-n").asBoolean(true);
            String filePattern = node.has("glob") ? node.get("glob").asText() : "*";

            if (pattern == null || pattern.isBlank()) {
                return "搜索模式不能为空";
            }

            Path baseDir = PathUtil.resolvePath(context.getWorkspaceDir(), pathValue);
            if (!Files.exists(baseDir)) {
                return "路径不存在: " + baseDir;
            }

            List<String> results = new ArrayList<>();
            int maxResults = 50;
            String globRegex = globToRegex(filePattern);

            try (Stream<Path> walk = Files.walk(baseDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            String relativePath = baseDir.relativize(p).toString().replace("\\", "/");
                            String fileName = p.getFileName().toString();
                            return fileName.matches(globRegex);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(path -> {
                        try {
                            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                            String fileRelativePath = baseDir.relativize(path).toString().replace("\\", "/");

                            java.util.regex.Pattern regexPattern;
                            if (ignoreCase) {
                                regexPattern = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
                            } else {
                                regexPattern = java.util.regex.Pattern.compile(pattern);
                            }

                            boolean fileHeaderAdded = false;
                            for (int i = 0; i < lines.size(); i++) {
                                if (regexPattern.matcher(lines.get(i)).find()) {
                                    if (!fileHeaderAdded) {
                                        results.add(fileRelativePath + ":");
                                        fileHeaderAdded = true;
                                    }
                                    String lineNum = showLineNumber ? String.format("%d\t", i + 1) : "";
                                    results.add("  " + lineNum + lines.get(i).trim());

                                    if (results.size() >= maxResults) {
                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // 跳过无法读取的文件
                        }
                    });
            }

            if (results.isEmpty()) {
                return "未找到匹配的内容";
            }

            String output = String.join("\n", results);
            if (output.length() > 5000) {
                output = output.substring(0, 5000) + "\n... (结果已截断)";
            }
            return output;
        });
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        sb.append(".*");
                        i += 2;
                        if (i < glob.length() && glob.charAt(i) == '/') {
                            sb.append("/?");
                            i++;
                        }
                    } else {
                        sb.append("[^/]*");
                        i++;
                    }
                    break;
                case '?':
                    sb.append(".");
                    i++;
                    break;
                case '.':
                    sb.append("\\.");
                    i++;
                    break;
                default:
                    sb.append(c);
                    i++;
            }
        }
        return sb.toString();
    }
}