package com.gacfox.meowclaw.interceptor;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptor;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.kit.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentLoggingInterceptor implements AgentInterceptor {
    @Override
    public AgentLoopResult intercept(AgentContext context, AgentInterceptorChain chain) {
        log.info("Agent input: {}", JsonUtil.dump(context.getMessages()));
        AgentLoopResult result = chain.next(context);
        log.info("Agent output: {}", JsonUtil.dump(result.getResponses()));
        return result;
    }
}
