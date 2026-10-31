package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoopIterationReminderInterceptorTest {

    private final AgentInterceptorChain chain = context -> AgentLoopResult.continueWith(List.of());

    private static AgentContext context() {
        return AgentContext.builder().messages(new ArrayList<>()).build();
    }

    @Test
    void doesNotRemindWithinFirstFiftyIterations() {
        LoopIterationReminderInterceptor interceptor = new LoopIterationReminderInterceptor();
        AgentContext context = context();

        for (int i = 0; i < 50; i++) {
            interceptor.intercept(context, chain);
        }

        assertThat(context.getMessages()).isEmpty();
    }

    @Test
    void remindsAtEveryFiftyCompletedIterations() {
        LoopIterationReminderInterceptor interceptor = new LoopIterationReminderInterceptor();
        AgentContext context = context();

        for (int i = 0; i < 101; i++) {
            interceptor.intercept(context, chain);
        }

        assertThat(context.getMessages()).hasSize(2);
        assertThat((String) context.getMessages().get(0).getContent())
                .contains("completed 50 ReAct iterations");
        assertThat((String) context.getMessages().get(1).getContent())
                .contains("completed 100 ReAct iterations");
    }

    @Test
    void doesNotDuplicateReminderAtSameIteration() {
        LoopIterationReminderInterceptor interceptor = new LoopIterationReminderInterceptor();
        AgentContext context = context();

        for (int i = 0; i < 52; i++) {
            interceptor.intercept(context, chain);
        }

        assertThat(context.getMessages()).hasSize(1);
    }
}
