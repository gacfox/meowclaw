package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.interceptor.agent.ToolCallIdStashInterceptor;
import com.gacfox.meowclaw.service.SubagentService;
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
    private static final String FINAL_ANSWER_TOOL = "final_answer";

    private final SubagentService subagentService;

    @Autowired
    public SpawnSubagentTool(SubagentService subagentService) {
        this.subagentService = subagentService;
    }

    @AgenticTool(name = TOOL_NAME, description = "创建子智能体在独立上下文中执行任务。工具立即返回，多个子任务并发运行；"
            + "所有子任务完成后其结果会自动汇总提供，届时再继续决策。allowed_tools必须是当前可用工具列表的子集，"
            + "且不允许包含spawn_subagent。")
    public String spawn(@AgenticToolParam(name = "param", description = "子智能体任务参数") SpawnSubagentParam param,
                        AgentContext context) {
        Object conversationId = context.getVariables().get("conversationId");
        if (!(conversationId instanceof Number)) {
            return "Error: spawn_subagent is not available in this context";
        }
        if (param.getPrompt() == null || param.getPrompt().isBlank()) {
            return "Error: prompt is required";
        }
        List<String> parentTools = context.getToolNames() == null ? List.of() : context.getToolNames();
        List<String> allowed = param.getAllowedTools() == null ? new ArrayList<>() : new ArrayList<>(param.getAllowedTools());
        // final_answer是框架内置的循环收口工具，子智能体天然具备，模型常会将其一并列入白名单，静默忽略
        allowed.removeIf(FINAL_ANSWER_TOOL::equals);
        for (String tool : allowed) {
            if (TOOL_NAME.equals(tool)) {
                return "Error: spawn_subagent cannot be used inside a subagent";
            }
            if (!parentTools.contains(tool)) {
                return "Error: tool '" + tool + "' is not in the parent agent's tool list";
            }
        }
        String toolCallId = Objects.toString(context.getVariables().get(ToolCallIdStashInterceptor.VARIABLE_KEY), null);
        String description = param.getDescription() == null || param.getDescription().isBlank()
                ? "子任务" : param.getDescription();
        return subagentService.spawn(((Number) conversationId).longValue(), toolCallId,
                param.getPrompt(), description, allowed);
    }
}
