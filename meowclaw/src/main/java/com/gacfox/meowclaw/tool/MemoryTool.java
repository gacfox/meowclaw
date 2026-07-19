package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.dto.MemoryNodeDTO;
import com.gacfox.meowclaw.service.MemoryService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.proarc.kit.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 长期记忆工具
 */
@Slf4j
@Component
public class MemoryTool {
    private final MemoryService memoryService;

    @Autowired
    public MemoryTool(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @AgenticTool(name = "memory_write", description = "写入一条长期记忆，type 必须是 fact/preference/rule 之一，写入成功返回OK，注意该工具是高成本耗时操作，相同内容严禁多次重复调用")
    public String write(@AgenticToolParam(name = "param", description = "写入参数") MemoryWriteParam param,
                        AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        Long conversationId = (Long) ctx.getVariables().get("conversationId");
        MemoryNodeDTO ignored = memoryService.write(agentId, param.getType(), param.getContent(), conversationId);
        return "OK";
    }

    @AgenticTool(name = "memory_recall", description = "根据查询召回相关长期记忆")
    public String recall(@AgenticToolParam(name = "param", description = "召回参数") MemoryRecallParam param,
                         AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        List<MemoryNodeDTO> nodes = memoryService.recall(agentId, param.getQuery(), param.getLimit());
        return JsonUtil.dump(nodes);
    }

    @AgenticTool(name = "memory_get", description = "按ID获取长期记忆，最多3条")
    public String get(@AgenticToolParam(name = "param", description = "获取参数") MemoryGetParam param,
                      AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        List<MemoryNodeDTO> nodes = memoryService.get(agentId, param.getIds());
        return JsonUtil.dump(nodes);
    }

    @AgenticTool(name = "memory_forget", description = "删除一条长期记忆")
    public String forget(@AgenticToolParam(name = "param", description = "删除参数") MemoryForgetParam param,
                         AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        memoryService.forget(agentId, param.getId());
        return "OK";
    }
}
