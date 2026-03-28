package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import com.gacfox.meowclaw.util.PathUtil;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 2000;
    private static final int DEFAULT_OFFSET = 0;
    private static final String NAME = "read";
    private static final String DESCRIPTION = "读取文件内容。默认路径相对于智能体工作区。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要读取的文件路径（相对路径默认在工作区内）"
                },
                "limit": {
                  "type": "integer",
                  "description": "要读取的行数，默认2000",
                  "default": 2000
                },
                "offset": {
                  "type": "integer",
                  "description": "要开始读取的行号（从1开始），默认0从头开始",
                  "default": 0
                }
              },
              "required": ["path"]
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
            String pathValue = node.get("path").asText();
            int limit = node.has("limit") ? node.get("limit").asInt(DEFAULT_LIMIT) : DEFAULT_LIMIT;
            int offset = node.has("offset") ? node.get("offset").asInt(DEFAULT_OFFSET) : DEFAULT_OFFSET;
            if (limit <= 0) {
                limit = DEFAULT_LIMIT;
            }
            if (offset < 0) {
                offset = 0;
            }
            if (pathValue == null || pathValue.isBlank()) {
                return "文件路径不能为空";
            }

            Path path = PathUtil.resolvePath(context.getWorkspaceDir(), pathValue);
            if (!Files.exists(path)) {
                return "文件不存在: " + path;
            }
            if (Files.isDirectory(path)) {
                return "路径是目录，无法读取: " + path;
            }

            Charset charset = Charset.defaultCharset();
            String content = Files.readString(path, charset);
            String[] lines = content.split("\n", -1);
            int totalLines = lines.length;

            if (offset >= totalLines) {
                return "文件只有 " + totalLines + " 行，offset 超出范围";
            }

            int endLine = Math.min(offset + limit, totalLines);
            StringBuilder sb = new StringBuilder();
            for (int i = offset; i < endLine; i++) {
                sb.append(i + 1).append("\t").append(lines[i]);
                if (i < endLine - 1) {
                    sb.append("\n");
                }
            }

            String result = sb.toString();
            if (endLine < totalLines) {
                result += "\n... (已截断，第 " + (offset + 1) + " - " + endLine + " 行，总共 " + totalLines + " 行)";
            }
            return result;
        });
    }
}
