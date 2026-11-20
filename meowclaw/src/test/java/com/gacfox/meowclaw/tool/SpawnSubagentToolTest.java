package com.gacfox.meowclaw.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.service.SubagentService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpawnSubagentToolTest {

    private final SubagentService subagentService = mock(SubagentService.class);
    private final SpawnSubagentTool tool = new SpawnSubagentTool(subagentService);

    private static AgentContext parentContext(List<String> toolNames) {
        return AgentContext.builder()
                .messages(new ArrayList<>())
                .toolNames(toolNames)
                .variables(new ConcurrentHashMap<>(Map.of("conversationId", 1L)))
                .build();
    }

    private static SpawnSubagentParam param(String prompt, List<String> allowedTools) {
        SpawnSubagentParam param = new SpawnSubagentParam();
        param.setPrompt(prompt);
        param.setDescription("测试子任务");
        param.setAllowedTools(allowedTools);
        return param;
    }

    @Test
    void schemaRequiresOnlyPrompt() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(tool);

        JsonNode schema = new ObjectMapper().readTree(
                registry.getAgenticTool(SpawnSubagentTool.TOOL_NAME).getJsonSchema());
        List<String> required = new ArrayList<>();
        schema.at("/function/parameters/required").forEach(node -> required.add(node.asText()));

        assertThat(required).containsExactly("prompt");
        assertThat(schema.at("/function/parameters/properties").has("agent_id")).isTrue();
        assertThat(schema.at("/function/parameters/properties").has("allowed_tools")).isTrue();
    }

    @Test
    void spawnsWithValidToolSubset() {
        when(subagentService.spawn(any(), any(), anyString(), anyString(), anyList())).thenReturn("ok");

        String result = tool.spawn(param("搜索资料", List.of("read", "grep")), parentContext(List.of("read", "grep", "spawn_subagent")));

        assertThat(result).isEqualTo("ok");
        verify(subagentService).spawn(org.mockito.ArgumentMatchers.eq(1L), any(), org.mockito.ArgumentMatchers.eq("搜索资料"),
                org.mockito.ArgumentMatchers.eq("测试子任务"), org.mockito.ArgumentMatchers.eq(List.of("read", "grep")));
    }

    @Test
    void rejectsNestedSpawn() {
        String result = tool.spawn(param("任务", List.of("spawn_subagent")), parentContext(List.of("spawn_subagent")));

        assertThat(result).contains("cannot be used inside a subagent");
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }

    @Test
    void rejectsToolOutsideParentList() {
        String result = tool.spawn(param("任务", List.of("bash")), parentContext(List.of("read")));

        assertThat(result).contains("not in the parent agent's tool list");
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }

    @Test
    void spawnsProfessionalSubagentWithoutParentToolSubsetRestriction() {
        SpawnSubagentParam param = param("审查代码", null);
        param.setAgentId(8L);
        when(subagentService.spawnProfessional(any(), any(), anyString(), anyString(), eq(8L))).thenReturn("ok");

        String result = tool.spawn(param, parentContext(List.of("spawn_subagent")));

        assertThat(result).isEqualTo("ok");
        verify(subagentService).spawnProfessional(eq(1L), any(), eq("审查代码"), eq("测试子任务"), eq(8L));
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }

    @Test
    void rejectsAllowedToolsForProfessionalSubagent() {
        SpawnSubagentParam param = param("审查代码", List.of("read"));
        param.setAgentId(8L);

        String result = tool.spawn(param, parentContext(List.of("read", "spawn_subagent")));

        assertThat(result).contains("only available for general subagents");
        verify(subagentService, never()).spawnProfessional(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsEmptyAllowedToolsForProfessionalSubagent() {
        SpawnSubagentParam param = param("审查代码", List.of());
        param.setAgentId(8L);

        String result = tool.spawn(param, parentContext(List.of("spawn_subagent")));

        assertThat(result).contains("only available for general subagents");
        verify(subagentService, never()).spawnProfessional(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsBlankPrompt() {
        String result = tool.spawn(param("  ", List.of("read")), parentContext(List.of("read")));

        assertThat(result).contains("prompt is required");
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }
}
