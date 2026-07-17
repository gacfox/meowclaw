package com.gacfox.meowclaw.interceptor;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.service.SystemPromptService;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptor;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 每轮 ReAct 循环前刷新系统提示词中的 CWD 等动态变量
 */
@Component
public class AgentSystemPromptRefreshInterceptor implements AgentInterceptor {

    private final AgentRepository agentRepository;
    private final SystemPromptService systemPromptService;

    @Autowired
    public AgentSystemPromptRefreshInterceptor(AgentRepository agentRepository, SystemPromptService systemPromptService) {
        this.agentRepository = agentRepository;
        this.systemPromptService = systemPromptService;
    }

    @Override
    public AgentLoopResult intercept(AgentContext context, AgentInterceptorChain chain) {
        Object agentIdObj = context.getVariables().get("agentId");
        if (!(agentIdObj instanceof Long agentId)) {
            return chain.next(context);
        }

        Agent agent = agentRepository.findById(agentId).orElse(null);
        if (agent == null) {
            return chain.next(context);
        }

        Object cwdObj = context.getVariables().get("cwd");
        String cwd = cwdObj instanceof String ? (String) cwdObj : null;
        String systemContent = systemPromptService.build(agent, cwd);

        boolean replaced = false;
        for (Message msg : context.getMessages()) {
            if (Message.ROLE_SYSTEM.equals(msg.getRole())) {
                msg.setContent(systemContent);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            context.getMessages().add(0, Message.builder()
                    .role(Message.ROLE_SYSTEM)
                    .content(systemContent)
                    .build());
        }

        return chain.next(context);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
