package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptor;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentLoggingInterceptor implements AgentInterceptor {
    private final JsonUtil jsonUtil;

    @Autowired
    public AgentLoggingInterceptor(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    @Override
    public AgentLoopResult intercept(AgentContext context, AgentInterceptorChain chain) {
        log.info("Agent input: {}", jsonUtil.dump(context.getMessages()));
        AgentLoopResult result = chain.next(context);
        log.info("Agent output: {}", jsonUtil.dump(result.getResponses()));
        return result;
    }
}
