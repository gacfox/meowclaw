package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptor;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;

/**
 * 长循环纠偏拦截器：每完成50轮ReAct循环，在下一次LLM调用前注入提醒，降低无效重复与死循环概率
 */
public class LoopIterationReminderInterceptor implements AgentInterceptor {
    private static final int REMINDER_INTERVAL = 50;

    private int iteration;

    @Override
    public AgentLoopResult intercept(AgentContext context, AgentInterceptorChain chain) {
        iteration++;
        if (iteration > REMINDER_INTERVAL && (iteration - 1) % REMINDER_INTERVAL == 0) {
            int completedIterations = iteration - 1;
            context.getMessages().add(Message.builder()
                    .role(Message.ROLE_USER)
                    .content("""
                            You have completed %d ReAct iterations without finishing.
                            Pause and reassess before continuing:
                            - Review what has been accomplished and what remains.
                            - Do not repeat the same reasoning or identical tool calls.
                            - If the task is complete, provide the final answer as plain content without calling tools.
                            - If stuck, change strategy or explain the blocker in your final response.
                            Continue only when there is useful work left.
                            """.formatted(completedIterations))
                    .build());
        }
        return chain.next(context);
    }

    @Override
    public int getOrder() {
        return -40;
    }
}
