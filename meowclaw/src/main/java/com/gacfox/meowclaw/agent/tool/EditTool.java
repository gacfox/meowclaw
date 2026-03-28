package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import com.gacfox.meowclaw.util.PathUtil;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class EditTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "edit";
    private static final String DESCRIPTION = "对文本文件进行替换编辑。默认路径相对于智能体工作区。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "要编辑的文件路径（相对路径默认在工作区内）"
                },
                "old": {
                  "type": "string",
                  "description": "要被替换的文本"
                },
                "new": {
                  "type": "string",
                  "description": "替换后的文本"
                },
                "replaceAll": {
                  "type": "boolean",
                  "description": "是否替换所有匹配，默认false",
                  "default": false
                }
              },
              "required": ["path", "old", "new"]
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
            String oldText = node.get("old").asText();
            String newText = node.get("new").asText("");
            boolean replaceAll = node.has("replaceAll") && node.get("replaceAll").asBoolean(false);
            if (pathValue == null || pathValue.isBlank()) {
                return "文件路径不能为空";
            }
            if (oldText == null || oldText.isEmpty()) {
                return "要替换的文本不能为空";
            }

            Path path = PathUtil.resolvePath(context.getWorkspaceDir(), pathValue);
            if (!Files.exists(path)) {
                return "文件不存在: " + path;
            }
            if (Files.isDirectory(path)) {
                return "路径是目录，无法编辑: " + path;
            }

            Charset charset = Charset.defaultCharset();
            String content = Files.readString(path, charset);
            if (!content.contains(oldText)) {
                return "未找到要替换的内容";
            }

            String updated = replaceAll
                    ? content.replace(oldText, newText)
                    : content.replaceFirst(java.util.regex.Pattern.quote(oldText), newText);
            Files.writeString(path, updated, charset, StandardOpenOption.TRUNCATE_EXISTING);
            return "编辑成功: " + path;
        });
    }
}
