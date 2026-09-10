package com.gacfox.meowclaw.tool;

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

    @AgenticTool(name = "memory_write", description = "写入一条长期记忆，type 必须是 fact/preference/rule 之一。系统会自动召回相似记忆并进行去重、更新或删除决策，写入前无需先 recall 查重，但相同内容严禁多次重复调用。该工具是高成本耗时操作")
    public String write(@AgenticToolParam(name = "param", description = "写入参数") MemoryWriteParam param,
                        AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        Long conversationId = (Long) ctx.getVariables().get("conversationId");
        return memoryService.write(agentId, param.getType(), param.getContent(), conversationId);
    }

    @AgenticTool(name = "memory_recall", description = "根据查询召回相关长期记忆")
    public String recall(@AgenticToolParam(name = "param", description = "召回参数") MemoryRecallParam param,
                         AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        List<com.gacfox.meowclaw.dto.MemoryNodeDTO> nodes = memoryService.recall(agentId, param.getQuery(), param.getLimit());
        return JsonUtil.dump(nodes);
    }
}
