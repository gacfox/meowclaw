package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.interceptor.agent.ToolCallIdStashInterceptor;
import com.gacfox.meowclaw.service.chat.SubagentService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 子智能体派发工具：在干净的独立上下文中创建一次性子智能体执行任务
 */
@Component
public class SpawnSubagentTool {
    public static final String TOOL_NAME = "spawn_subagent";

    private final SubagentService subagentService;

    @Autowired
    public SpawnSubagentTool(SubagentService subagentService) {
        this.subagentService = subagentService;
    }

    @AgenticTool(name = TOOL_NAME, description = "创建子智能体在独立上下文中执行任务。工具立即返回，多个子任务并发运行；"
            + "所有子任务完成后其结果会自动汇总提供，届时再继续决策。agent_id缺省时创建general子智能体，"
            + "allowed_tools仅是当前可用工具列表的子集且不允许包含spawn_subagent；传入agent_id时创建对应专业子智能体，"
            + "使用其自身完整配置，不能同时传allowed_tools。")
    public String spawn(@AgenticToolParam(name = "param", description = "子智能体任务参数") SpawnSubagentParam param,
                        AgentContext context) {
        Object conversationId = context.getVariables().get("conversationId");
        if (!(conversationId instanceof Number)) {
            return "Error: spawn_subagent is not available in this context";
        }
        if (param.getPrompt() == null || param.getPrompt().isBlank()) {
            return "Error: prompt is required";
        }
        String toolCallId = Objects.toString(context.getVariables().get(ToolCallIdStashInterceptor.VARIABLE_KEY), null);
        String description = param.getDescription() == null || param.getDescription().isBlank()
                ? "子任务" : param.getDescription();
        Long parentConversationId = ((Number) conversationId).longValue();
        if (param.getAgentId() != null) {
            if (param.getAllowedTools() != null) {
                return "Error: allowed_tools is only available for general subagents";
            }
            return subagentService.spawnProfessional(parentConversationId, toolCallId,
                    param.getPrompt(), description, param.getAgentId());
        }

        List<String> parentTools = context.getToolNames() == null ? List.of() : context.getToolNames();
        List<String> allowed = param.getAllowedTools() == null ? new ArrayList<>() : new ArrayList<>(param.getAllowedTools());
        for (String tool : allowed) {
            if (TOOL_NAME.equals(tool)) {
                return "Error: spawn_subagent cannot be used inside a subagent";
            }
            if (!parentTools.contains(tool)) {
                return "Error: tool '" + tool + "' is not in the parent agent's tool list";
            }
        }
        return subagentService.spawn(parentConversationId, toolCallId, param.getPrompt(), description, allowed);
    }
}
