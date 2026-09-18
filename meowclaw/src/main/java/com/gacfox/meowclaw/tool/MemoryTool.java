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

    @AgenticTool(name = "memory_write", description = "写入一条长期记忆，type 必须是 fact/preference/rule 之一。写入是异步的：提交后立即返回，系统后台自动完成相似记忆查重、结构抽取与入库，无需等待；相同内容严禁多次重复调用")
    public String write(@AgenticToolParam(name = "param", description = "写入参数") MemoryWriteParam param,
                        AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        Long conversationId = (Long) ctx.getVariables().get("conversationId");
        memoryService.writeAsync(agentId, param.getType(), param.getContent(), conversationId);
        return "已提交后台写入";
    }

    @AgenticTool(name = "memory_recall", description = "根据查询召回相关长期记忆")
    public String recall(@AgenticToolParam(name = "param", description = "召回参数") MemoryRecallParam param,
                         AgentContext ctx) {
        Long agentId = (Long) ctx.getVariables().get("agentId");
        List<com.gacfox.meowclaw.dto.MemoryNodeDTO> nodes = memoryService.recall(agentId, param.getQuery(), param.getLimit());
        return JsonUtil.dump(nodes);
    }
}
