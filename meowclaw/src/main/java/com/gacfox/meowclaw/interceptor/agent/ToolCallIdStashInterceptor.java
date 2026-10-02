package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.proarc.agentic.agent.ToolInvocation;
import com.gacfox.proarc.agentic.agent.interceptor.ToolCallChain;
import com.gacfox.proarc.agentic.agent.interceptor.ToolCallInterceptor;

/**
 * 将当前工具调用ID暂存到上下文变量，使工具执行时可感知自身调用ID（如spawn_subagent关联事件）
 */
public class ToolCallIdStashInterceptor implements ToolCallInterceptor {
    public static final String VARIABLE_KEY = "__currentToolCallId";

    @Override
    public String intercept(ToolInvocation invocation, ToolCallChain chain) throws Exception {
        invocation.getAgentContext().getVariables().put(VARIABLE_KEY, invocation.getToolCallId());
        try {
            return chain.proceed(invocation);
        } finally {
            invocation.getAgentContext().getVariables().remove(VARIABLE_KEY);
        }
    }
}
