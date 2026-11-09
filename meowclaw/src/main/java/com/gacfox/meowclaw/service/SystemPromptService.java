package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.ContextRecap;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.gacfox.meowclaw.util.FrontmatterParseUtil;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.prompt.PromptTemplate;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 系统提示词与用户轮次上下文组装服务
 */
@Slf4j
@Service
public class SystemPromptService {
    @Value("classpath:prompt/system-prompt.md")
    private Resource systemPromptResource;

    @Value("classpath:prompt/turn-context.md")
    private Resource turnContextResource;

    private final ChatEventBatchRepository batchRepository;
    private final ContextRecapRepository recapRepository;
    private final JsonUtil jsonUtil;
    private String systemPromptTemplate;
    private String turnContextTemplate;

    @Autowired
    public SystemPromptService(ChatEventBatchRepository batchRepository,
                               ContextRecapRepository recapRepository,
                               JsonUtil jsonUtil) {
        this.batchRepository = batchRepository;
        this.recapRepository = recapRepository;
        this.jsonUtil = jsonUtil;
    }

    @PostConstruct
    void loadTemplates() throws IOException {
        try (InputStream input = systemPromptResource.getInputStream()) {
            systemPromptTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream input = turnContextResource.getInputStream()) {
            turnContextTemplate = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 构建不含会话动态数据的系统提示词
     */
    public String buildSystemPrompt(Agent agent) {
        Map<String, Object> vars = new HashMap<>();
        String persona = agent.getPersona();
        if (persona != null && !persona.isBlank()) {
            vars.put("persona", persona.strip());
        }
        return PromptTemplate.build(systemPromptTemplate, vars);
    }

    /**
     * 构建当前用户轮次使用的动态上下文
     */
    public PromptContext buildPromptContext(Agent agent, Long conversationId, String cwd, String memories) {
        List<ContextRecap> recaps = recapRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<Long> recapIds = recaps.stream().map(ContextRecap::getId).toList();
        Optional<ChatEventBatch> previousBatch = batchRepository
                .findFirstByConversationIdAndTypeAndStatusAndPromptContextJsonIsNotNullOrderByCreatedAtDesc(
                        conversationId, "USER", "COMPLETED");
        List<Long> previousRecapIds = previousBatch
                .map(batch -> jsonUtil.load(batch.getPromptContextJson(), PromptContext.class).recapIds())
                .orElse(List.of());
        boolean previousContextCompressed = previousBatch.map(batch -> recaps.stream().anyMatch(recap ->
                "PROACTIVE".equals(recap.getType())
                        && recap.getFromBatchId() <= batch.getId()
                        && recap.getToBatchId() >= batch.getId())).orElse(false);

        String recap = null;
        String recapMode = null;
        if (!recaps.isEmpty()) {
            if (previousContextCompressed) {
                recapMode = "replace";
                recap = recaps.stream().map(ContextRecap::getContent).collect(Collectors.joining("\n\n"));
            } else if (previousRecapIds.isEmpty()) {
                recapMode = "snapshot";
                recap = recaps.stream().map(ContextRecap::getContent).collect(Collectors.joining("\n\n"));
            } else if (recapIds.containsAll(previousRecapIds)) {
                List<ContextRecap> added = recaps.stream()
                        .filter(item -> !previousRecapIds.contains(item.getId())).toList();
                if (!added.isEmpty()) {
                    recapMode = "append";
                    recap = added.stream().map(ContextRecap::getContent).collect(Collectors.joining("\n\n"));
                }
            } else {
                recapMode = "replace";
                recap = recaps.stream().map(ContextRecap::getContent).collect(Collectors.joining("\n\n"));
            }
        }

        String workspaceFolder = agent.getWorkspaceFolder();
        boolean hasWorkspace = workspaceFolder != null && !workspaceFolder.isBlank();
        List<SkillSummary> skills = hasWorkspace ? listInstalledSkills(workspaceFolder) : List.of();
        Map<String, Object> vars = new HashMap<>();
        vars.put("currentDate", LocalDate.now());
        vars.put("osName", System.getProperty("os.name"));
        vars.put("hasWorkspace", hasWorkspace);
        vars.put("workspacePath", workspaceFolder);
        vars.put("cwd", cwd);
        vars.put("hasSkills", !skills.isEmpty());
        vars.put("skills", skills);
        vars.put("hasRecap", recap != null);
        vars.put("recap", recap);
        vars.put("recapMode", recapMode);
        boolean hasMemories = memories != null && !memories.isBlank();
        vars.put("hasMemories", hasMemories);
        vars.put("memories", hasMemories ? memories.strip() : null);
        return new PromptContext(PromptTemplate.build(turnContextTemplate, vars).strip(), recapIds);
    }

    /**
     * 将服务端上下文与用户原文组合为模型可见的用户消息
     */
    public static String renderUserMessage(PromptContext context, String userContent) {
        return context.content() + "\n\n<user_request>\n" + userContent + "\n</user_request>";
    }

    public record PromptContext(String content, List<Long> recapIds) {
    }

    public record SkillSummary(String name, String description) {
    }

    private List<SkillSummary> listInstalledSkills(String workspacePath) {
        Path skillRoot = Paths.get(workspacePath, ".skill");
        if (!Files.isDirectory(skillRoot)) return List.of();
        List<SkillSummary> result = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(skillRoot)) {
            dirs.filter(Files::isDirectory).forEach(directory -> {
                Path markdown = directory.resolve("SKILL.md");
                if (!Files.isRegularFile(markdown)) return;
                try {
                    String content = Files.readString(markdown, StandardCharsets.UTF_8);
                    Map<String, Object> frontmatter = FrontmatterParseUtil.parse(content);
                    Object name = frontmatter.get("name");
                    if (name == null) return;
                    Object description = frontmatter.get("description");
                    result.add(new SkillSummary(String.valueOf(name),
                            description == null ? null : String.valueOf(description)));
                } catch (Exception e) {
                    log.warn("Failed to read SKILL.md at {}", markdown, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list installed skills at {}", skillRoot, e);
        }
        result.sort(Comparator.comparing(SkillSummary::name));
        return result;
    }
}
