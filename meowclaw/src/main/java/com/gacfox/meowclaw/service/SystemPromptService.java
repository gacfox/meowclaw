package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.util.FrontmatterParser;
import com.gacfox.proarc.agentic.prompt.PromptTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 系统提示词组装：基于 Mustache 模板渲染 persona / workspace / skills 等片段。
 */
@Slf4j
@Service
public class SystemPromptService {

    private static final String TEMPLATE_LOCATION = "classpath:prompt/system-prompt.md";

    @Value(TEMPLATE_LOCATION)
    private Resource templateResource;

    private String template;

    @PostConstruct
    void loadTemplate() throws IOException {
        try (InputStream is = templateResource.getInputStream()) {
            template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 构建智能体的系统提示词（即 system message 内容）。
     */
    public String build(Agent agent) {
        Map<String, Object> vars = new HashMap<>();

        String persona = agent.getPersona();
        if (persona != null && !persona.isBlank()) {
            vars.put("persona", persona.strip());
        }

        String workspaceFolder = agent.getWorkspaceFolder();
        boolean hasWorkspace = workspaceFolder != null && !workspaceFolder.isBlank();
        vars.put("hasWorkspace", hasWorkspace);
        vars.put("workspacePath", workspaceFolder);

        List<SkillSummary> skills = hasWorkspace ? listInstalledSkills(workspaceFolder) : List.of();
        vars.put("hasSkills", !skills.isEmpty());
        vars.put("skills", skills);

        return PromptTemplate.build(template, vars);
    }

    public record SkillSummary(String name, String description) {}

    /**
     * 扫描工作区 {@code .skill/<name>/SKILL.md}，仅依赖文件系统，与技能包管理模块完全解耦。
     */
    private List<SkillSummary> listInstalledSkills(String workspacePath) {
        Path skillRoot = Paths.get(workspacePath, ".skill");
        if (!Files.isDirectory(skillRoot)) return List.of();
        List<SkillSummary> result = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(skillRoot)) {
            dirs.filter(Files::isDirectory).forEach(d -> {
                Path md = d.resolve("SKILL.md");
                if (!Files.isRegularFile(md)) return;
                try {
                    String content = Files.readString(md, StandardCharsets.UTF_8);
                    Map<String, Object> fm = FrontmatterParser.parse(content);
                    Object name = fm.get("name");
                    if (name == null) return;
                    Object desc = fm.get("description");
                    result.add(new SkillSummary(String.valueOf(name),
                            desc == null ? null : String.valueOf(desc)));
                } catch (Exception e) {
                    log.warn("Failed to read SKILL.md at {}", md, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list installed skills at {}", skillRoot, e);
        }
        return result;
    }
}
