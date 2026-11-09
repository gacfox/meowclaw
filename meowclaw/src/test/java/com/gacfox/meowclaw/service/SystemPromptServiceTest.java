package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import com.gacfox.meowclaw.entity.ContextRecap;
import com.gacfox.meowclaw.repository.ChatEventBatchRepository;
import com.gacfox.meowclaw.repository.ContextRecapRepository;
import com.gacfox.meowclaw.service.SystemPromptService.PromptContext;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemPromptServiceTest {
    @TempDir
    Path workspace;

    private ChatEventBatchRepository batchRepository;
    private ContextRecapRepository recapRepository;
    private JsonUtil jsonUtil;
    private SystemPromptService service;
    private Agent agent;

    @BeforeEach
    void setUp() throws Exception {
        batchRepository = mock(ChatEventBatchRepository.class);
        recapRepository = mock(ContextRecapRepository.class);
        jsonUtil = new JsonUtil(new ObjectMapper());
        service = new SystemPromptService(batchRepository, recapRepository, jsonUtil);
        ReflectionTestUtils.setField(service, "systemPromptResource",
                new ClassPathResource("prompt/system-prompt.md"));
        ReflectionTestUtils.setField(service, "turnContextResource",
                new ClassPathResource("prompt/turn-context.md"));
        service.loadTemplates();

        agent = new Agent();
        agent.setWorkspaceFolder(workspace.toString());
        when(batchRepository.findFirstByConversationIdAndTypeAndStatusAndPromptContextJsonIsNotNullOrderByCreatedAtDesc(
                eq(1L), anyString(), anyString())).thenReturn(Optional.empty());
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());
    }

    @Test
    void systemPromptIsStableAndContainsWorkspaceNavigationRules() {
        agent.setPersona("  你是测试助手。  ");

        String first = service.buildSystemPrompt(agent);
        String second = service.buildSystemPrompt(agent);

        assertThat(first).isEqualTo(second);
        assertThat(first).contains("你是测试助手。", "CWD已经是目标目录", "demo/pom.xml");
        assertThat(first).doesNotContain("currentTime", workspace.toString(), "retrieved_memories\">");
    }

    @Test
    void promptContextContainsRuntimeDataAndWrapsOriginalUserInput() {
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(recap(10L, "旧摘要")));

        PromptContext context = service.buildPromptContext(agent, 1L, workspace.toString(), "记住使用 Maven");
        String message = SystemPromptService.renderUserMessage(context, "分析项目");

        assertThat(message).contains("<runtime_context>", workspace.toString(),
                "<recap_update mode=\"snapshot\">", "旧摘要", "记住使用 Maven",
                "<user_request>\n分析项目\n</user_request>");
        assertThat(context.recapIds()).containsExactly(10L);
    }

    @Test
    void promptContextAppendsOnlyNewRecaps() {
        PromptContext previous = new PromptContext("previous", List.of(10L));
        ChatEventBatch previousBatch = new ChatEventBatch();
        previousBatch.setPromptContextJson(jsonUtil.dump(previous));
        when(batchRepository.findFirstByConversationIdAndTypeAndStatusAndPromptContextJsonIsNotNullOrderByCreatedAtDesc(
                1L, "USER", "COMPLETED")).thenReturn(Optional.of(previousBatch));
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(recap(10L, "旧摘要"), recap(11L, "新增摘要")));

        PromptContext context = service.buildPromptContext(agent, 1L, workspace.toString(), null);

        assertThat(context.content()).contains("<recap_update mode=\"append\">", "新增摘要")
                .doesNotContain("旧摘要");
        assertThat(context.recapIds()).containsExactly(10L, 11L);
    }

    @Test
    void promptContextReplacesRecapsAfterRollup() {
        PromptContext previous = new PromptContext("previous", List.of(10L, 11L));
        ChatEventBatch previousBatch = new ChatEventBatch();
        previousBatch.setPromptContextJson(jsonUtil.dump(previous));
        when(batchRepository.findFirstByConversationIdAndTypeAndStatusAndPromptContextJsonIsNotNullOrderByCreatedAtDesc(
                1L, "USER", "COMPLETED")).thenReturn(Optional.of(previousBatch));
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(recap(20L, "合并摘要")));

        PromptContext context = service.buildPromptContext(agent, 1L, workspace.toString(), null);

        assertThat(context.content()).contains("<recap_update mode=\"replace\">", "合并摘要");
        assertThat(context.recapIds()).containsExactly(20L);
    }

    @Test
    void promptContextReplacesRecapsWhenPreviousContextWasCompressed() {
        PromptContext previous = new PromptContext("previous", List.of(10L));
        ChatEventBatch previousBatch = new ChatEventBatch();
        previousBatch.setId(5L);
        previousBatch.setPromptContextJson(jsonUtil.dump(previous));
        when(batchRepository.findFirstByConversationIdAndTypeAndStatusAndPromptContextJsonIsNotNullOrderByCreatedAtDesc(
                1L, "USER", "COMPLETED")).thenReturn(Optional.of(previousBatch));
        ContextRecap existing = recap(10L, "仍有效的旧摘要");
        ContextRecap proactive = recap(11L, "主动压缩摘要");
        proactive.setType("PROACTIVE");
        proactive.setFromBatchId(5L);
        proactive.setToBatchId(5L);
        when(recapRepository.findByConversationIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(existing, proactive));

        PromptContext context = service.buildPromptContext(agent, 1L, workspace.toString(), null);

        assertThat(context.content()).contains("<recap_update mode=\"replace\">",
                "仍有效的旧摘要", "主动压缩摘要");
    }

    @Test
    void installedSkillsHaveDeterministicOrder() throws Exception {
        writeSkill("zeta", "zeta", "最后");
        writeSkill("alpha", "alpha", "最先");

        PromptContext context = service.buildPromptContext(agent, 1L, workspace.toString(), null);

        assertThat(context.content()).contains("name=\"alpha\"", "name=\"zeta\"");
        assertThat(context.content().indexOf("name=\"alpha\""))
                .isLessThan(context.content().indexOf("name=\"zeta\""));
    }

    private ContextRecap recap(Long id, String content) {
        ContextRecap recap = new ContextRecap();
        recap.setId(id);
        recap.setContent(content);
        return recap;
    }

    private void writeSkill(String directory, String name, String description) throws Exception {
        Path skill = workspace.resolve(".skill").resolve(directory);
        Files.createDirectories(skill);
        Files.writeString(skill.resolve("SKILL.md"),
                "---\nname: " + name + "\ndescription: " + description + "\n---\n");
    }
}
