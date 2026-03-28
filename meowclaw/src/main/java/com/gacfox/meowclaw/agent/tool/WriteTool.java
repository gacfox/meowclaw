package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import com.gacfox.meowclaw.util.PathUtil;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class WriteTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "write";
    private static final String DESCRIPTION = "写入文本文件内容。默认路径相对于智能体工作区。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要写入的文件路径（相对路径默认在工作区内）"
                },
                "content": {
                  "type": "string",
                  "description": "要写入的内容"
                },
                "append": {
                  "type": "boolean",
                  "description": "是否追加到文件末尾，默认false",
                  "default": false
                }
              },
              "required": ["path", "content"]
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
            String content = node.get("content").asText("");
            boolean append = node.has("append") && node.get("append").asBoolean(false);
            if (pathValue == null || pathValue.isBlank()) {
                return "文件路径不能为空";
            }

            Path path = PathUtil.resolvePath(context.getWorkspaceDir(), pathValue);
            Path parent = path.getParent();
            if (parent != null) {
                Path ignored = Files.createDirectories(parent);
            }

            Charset charset = Charset.defaultCharset();
            if (append) {
                Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(path, content, charset, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            return "写入成功: " + path;
        });
    }
}
