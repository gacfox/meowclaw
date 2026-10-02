package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.meowclaw.service.SubagentRegistry;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentJoinInterceptorTest {

    private static AgentContext contextWithConversation(long conversationId) {
        return AgentContext.builder()
                .messages(new ArrayList<>())
                .variables(new ConcurrentHashMap<>(Map.of("conversationId", conversationId)))
                .build();
    }

    @Test
    void proceedsDirectlyWhenNoChildren() {
        SubagentRegistry registry = new SubagentRegistry();
        SubagentJoinInterceptor interceptor = new SubagentJoinInterceptor(registry);
        AgentContext ctx = contextWithConversation(1L);
        AtomicBoolean proceeded = new AtomicBoolean();

        interceptor.intercept(ctx, c -> {
            proceeded.set(true);
            return AgentLoopResult.continueWith(List.of());
        });

        assertThat(proceeded).isTrue();
        assertThat(ctx.getMessages()).isEmpty();
    }

    @Test
    void injectsAggregatedResultsWhenAllChildrenComplete() {
        SubagentRegistry registry = new SubagentRegistry();
        registry.register(1L, new SubagentRegistry.ChildHandle(11L, 100L, "任务一", CompletableFuture.completedFuture("结果A")));
        registry.register(1L, new SubagentRegistry.ChildHandle(12L, 101L, "任务二", CompletableFuture.completedFuture("结果B")));
        SubagentJoinInterceptor interceptor = new SubagentJoinInterceptor(registry);
        AgentContext ctx = contextWithConversation(1L);
        AtomicBoolean proceeded = new AtomicBoolean();

        interceptor.intercept(ctx, c -> {
            proceeded.set(true);
            return AgentLoopResult.continueWith(List.of());
        });

        assertThat(proceeded).isTrue();
        assertThat(registry.childrenOf(1L)).isEmpty();
        assertThat(ctx.getMessages()).hasSize(1);
        String content = (String) ctx.getMessages().get(0).getContent();
        assertThat(content).contains("任务一", "结果A", "任务二", "结果B");
    }

    @Test
    void stopsWaitingWhenStopRequested() {
        SubagentRegistry registry = new SubagentRegistry();
        registry.register(1L, new SubagentRegistry.ChildHandle(11L, null, "任务", new CompletableFuture<>()));
        SubagentJoinInterceptor interceptor = new SubagentJoinInterceptor(registry);
        AgentContext ctx = contextWithConversation(1L);
        ctx.requestStop();
        AtomicBoolean proceeded = new AtomicBoolean();

        interceptor.intercept(ctx, c -> {
            proceeded.set(true);
            return AgentLoopResult.continueWith(List.of());
        });

        assertThat(proceeded).isFalse();
        assertThat(ctx.getMessages()).isEmpty();
    }

    @Test
    void waitsUntilChildrenComplete() throws Exception {
        SubagentRegistry registry = new SubagentRegistry();
        CompletableFuture<String> pending = new CompletableFuture<>();
        registry.register(1L, new SubagentRegistry.ChildHandle(11L, null, "任务", pending));
        SubagentJoinInterceptor interceptor = new SubagentJoinInterceptor(registry);
        AgentContext ctx = contextWithConversation(1L);
        AtomicBoolean proceeded = new AtomicBoolean();

        Thread joinThread = new Thread(() -> interceptor.intercept(ctx, c -> {
            proceeded.set(true);
            return AgentLoopResult.continueWith(List.of());
        }));
        joinThread.start();
        Thread.sleep(500);
        assertThat(proceeded).isFalse();

        pending.complete("完成");
        joinThread.join(5000);
        assertThat(proceeded).isTrue();
        assertThat(ctx.getMessages()).hasSize(1);
    }
}
