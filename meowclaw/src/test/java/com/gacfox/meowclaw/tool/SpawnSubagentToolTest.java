package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.service.SubagentService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void silentlyDropsFinalAnswerFromAllowedTools() {
        when(subagentService.spawn(any(), any(), anyString(), anyString(), anyList())).thenReturn("ok");

        String result = tool.spawn(param("任务", List.of("read", "final_answer")), parentContext(List.of("read")));

        assertThat(result).isEqualTo("ok");
        verify(subagentService).spawn(any(), any(), any(), any(),
                org.mockito.ArgumentMatchers.eq(List.of("read")));
    }

    @Test
    void rejectsToolOutsideParentList() {
        String result = tool.spawn(param("任务", List.of("exec")), parentContext(List.of("read")));

        assertThat(result).contains("not in the parent agent's tool list");
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }

    @Test
    void rejectsBlankPrompt() {
        String result = tool.spawn(param("  ", List.of("read")), parentContext(List.of("read")));

        assertThat(result).contains("prompt is required");
        verify(subagentService, never()).spawn(any(), any(), any(), any(), anyList());
    }
}
