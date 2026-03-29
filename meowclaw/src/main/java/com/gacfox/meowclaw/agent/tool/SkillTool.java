package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.util.PathUtil;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class SkillTool implements Tool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String NAME = "skill";
    private static final String DESCRIPTION = "读取指定技能的 SKILL.md 内容。";
    private static final String PARAMETERS = """
            {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "description": "技能名称（kebab-case）"
                }
              },
              "required": ["name"]
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
            String skillName = node.has("name") ? node.get("name").asText() : null;
            if (skillName == null || skillName.isBlank()) {
                return "技能名称不能为空";
            }

            Path skillsDir = PathUtil.resolvePath(context.getWorkspaceDir(), "skills");
            Path skillDir = skillsDir.resolve(skillName).normalize();
            Path skillFile = skillDir.resolve("SKILL.md").normalize();
            if (!skillFile.startsWith(skillsDir.normalize())) {
                return "非法技能路径";
            }
            if (!Files.exists(skillFile)) {
                return "技能不存在或未部署: " + skillName;
            }
            if (Files.isDirectory(skillFile)) {
                return "技能文件无效: " + skillFile;
            }
            return Files.readString(skillFile, Charset.defaultCharset());
        });
    }
}
