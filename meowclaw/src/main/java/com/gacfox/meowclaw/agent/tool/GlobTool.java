package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import com.gacfox.meowclaw.util.PathUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class GlobTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "glob";
    private static final String DESCRIPTION = "快速搜索文件名，类似于 find 或 ls **/*.js。默认路径相对于智能体工作区。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要搜索的目录路径（相对路径默认在工作区内），默认为工作区根目录"
                },
                "pattern": {
                  "type": "string",
                  "description": " glob 模式，例如 **/*.java 或 *.tsx"
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
            String pathValue = node.has("path") ? node.get("path").asText() : "";

            if (pattern == null || pattern.isBlank()) {
                return "glob 模式不能为空";
            }

            Path baseDir = PathUtil.resolvePath(context.getWorkspaceDir(), pathValue);
            if (!Files.exists(baseDir)) {
                return "目录不存在: " + baseDir;
            }
            if (!Files.isDirectory(baseDir)) {
                return "路径不是目录: " + baseDir;
            }

            String regex = globToRegex(pattern);
            List<String> matches = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(baseDir)) {
                walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        try {
                            String relativePath = baseDir.relativize(p).toString().replace("\\", "/");
                            return relativePath.matches(regex);
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .forEach(p -> {
                        String relativePath = baseDir.relativize(p).toString().replace("\\", "/");
                        matches.add(relativePath);
                    });
            }

            if (matches.isEmpty()) {
                return "未找到匹配的文件";
            }

            matches.sort((a, b) -> {
                try {
                    Path pa = baseDir.resolve(a);
                    Path pb = baseDir.resolve(b);
                    long ta = Files.getLastModifiedTime(pa).toMillis();
                    long tb = Files.getLastModifiedTime(pb).toMillis();
                    return Long.compare(tb, ta);
                } catch (Exception e) {
                    return a.compareTo(b);
                }
            });

            int maxResults = 100;
            if (matches.size() > maxResults) {
                return String.join("\n", matches.subList(0, maxResults))
                    + "\n... (还有 " + (matches.size() - maxResults) + " 个文件)";
            }

            return String.join("\n", matches);
        });
    }

    private String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
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
                case '+':
                case '(':
                case ')':
                case '^':
                case '$':
                case '|':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                    sb.append("\\").append(c);
                    i++;
                    break;
                default:
                    sb.append(c);
                    i++;
            }
        }
        sb.append("$");
        return sb.toString();
    }
}