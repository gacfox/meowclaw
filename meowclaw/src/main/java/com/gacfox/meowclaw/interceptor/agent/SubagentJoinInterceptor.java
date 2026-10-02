package com.gacfox.meowclaw.interceptor.agent;

import com.gacfox.meowclaw.service.SubagentRegistry;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.agent.AgentLoopResult;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptor;
import com.gacfox.proarc.agentic.agent.interceptor.AgentInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 子智能体汇合拦截器：每轮LLM调用前检查父会话是否存在未完成的子任务，
 * 存在则挂起等待全部完成（协作式响应停止请求），随后将汇总结果注入消息历史
 */
@Slf4j
public class SubagentJoinInterceptor implements AgentInterceptor {
    private static final long POLL_INTERVAL_MS = 200;

    private final SubagentRegistry subagentRegistry;

    public SubagentJoinInterceptor(SubagentRegistry subagentRegistry) {
        this.subagentRegistry = subagentRegistry;
    }

    @Override
    public AgentLoopResult intercept(AgentContext context, AgentInterceptorChain chain) {
        Object conversationId = context.getVariables().get("conversationId");
        if (!(conversationId instanceof Number)) {
            return chain.next(context);
        }
        Long parentId = ((Number) conversationId).longValue();
        List<SubagentRegistry.ChildHandle> children = subagentRegistry.childrenOf(parentId);
        if (children.isEmpty()) {
            return chain.next(context);
        }

        while (children.stream().anyMatch(h -> !h.future().isDone())) {
            if (context.isStopRequested()) {
                return AgentLoopResult.continueWith(List.of());
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return AgentLoopResult.continueWith(List.of());
            }
        }

        StringBuilder aggregated = new StringBuilder("所有子智能体任务已执行完毕，汇总结果如下：\n");
        for (int i = 0; i < children.size(); i++) {
            SubagentRegistry.ChildHandle handle = children.get(i);
            aggregated.append("\n【子任务").append(i + 1).append("】").append(handle.description()).append('\n')
                    .append(handle.future().join()).append('\n');
        }
        context.getMessages().add(Message.builder()
                .role(Message.ROLE_USER).content(aggregated.toString()).build());
        subagentRegistry.clear(parentId);
        log.info("子任务汇总已注入: conversationId={}, count={}", parentId, children.size());
        return chain.next(context);
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
