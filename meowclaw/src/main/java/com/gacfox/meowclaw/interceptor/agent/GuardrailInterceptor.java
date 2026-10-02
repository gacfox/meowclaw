package com.gacfox.meowclaw.interceptor.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.GuardrailPolicy;
import com.gacfox.meowclaw.service.GuardrailPolicyEngine;
import com.gacfox.meowclaw.service.GuardrailPolicyService;
import com.gacfox.proarc.agentic.agent.ToolInvocation;
import com.gacfox.proarc.agentic.agent.interceptor.ToolCallChain;
import com.gacfox.proarc.agentic.agent.interceptor.ToolCallInterceptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 安全护栏拦截器：按会话生效策略对每次工具调用求值，
 * allow直接放行，deny返回拒绝说明，ask挂起等待用户审批
 */
@Slf4j
public class GuardrailInterceptor implements ToolCallInterceptor {
    private static final String FINAL_ANSWER_TOOL = "final_answer";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 审批结果
     */
    public enum ApprovalOutcome {
        APPROVED, REJECTED, INTERRUPTED
    }

    /**
     * 审批等待回调，由编排层（ChatService）实现，阻塞至用户决定或执行被中断
     */
    @FunctionalInterface
    public interface ApprovalHandler {
        ApprovalOutcome await(ToolInvocation invocation, String policyName, int matchedRuleIndex);
    }

    private final GuardrailPolicyService guardrailPolicyService;
    private final ApprovalHandler approvalHandler;

    public GuardrailInterceptor(GuardrailPolicyService guardrailPolicyService, ApprovalHandler approvalHandler) {
        this.guardrailPolicyService = guardrailPolicyService;
        this.approvalHandler = approvalHandler;
    }

    @Override
    public String intercept(ToolInvocation invocation, ToolCallChain chain) throws Exception {
        String toolName = invocation.getToolName();
        if (FINAL_ANSWER_TOOL.equals(toolName)) {
            return chain.proceed(invocation);
        }
        Object conversationId = invocation.getAgentContext().getVariables().get("conversationId");
        Object cwd = invocation.getAgentContext().getVariables().get("cwd");
        GuardrailPolicy policy = guardrailPolicyService.resolvePolicy(((Number) conversationId).longValue());
        GuardrailPolicyEngine.Decision decision = GuardrailPolicyEngine.evaluate(
                policy.getConfigJson(), toolName, extractPath(invocation.getArguments()),
                cwd instanceof String s ? s : null);

        if (decision.isAllow()) {
            return chain.proceed(invocation);
        }
        if (decision.isAsk()) {
            log.info("工具调用等待用户审批: tool={}, conversationId={}, policy={}", toolName, conversationId, policy.getName());
            ApprovalOutcome outcome = approvalHandler.await(invocation, policy.getName(), decision.matchedRuleIndex());
            return switch (outcome) {
                case APPROVED -> chain.proceed(invocation);
                case REJECTED -> "The user rejected this tool call.";
                case INTERRUPTED -> "Cancelled: the agent execution was stopped by request.";
            };
        }
        String denied = "Error: this tool call was denied by guardrail policy '" + policy.getName() + "'"
                + (decision.matchedRuleIndex() >= 0 ? " (rule #" + (decision.matchedRuleIndex() + 1) + ")" : " (defaultDecision)");
        log.info("工具调用被策略拒绝: tool={}, conversationId={}, {}", toolName, conversationId, denied);
        return denied;
    }

    /**
     * 提取工具参数中的path字段，内置文件类工具统一使用该字段名，无path参数返回null
     */
    private static String extractPath(String arguments) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(arguments);
            JsonNode path = node == null ? null : node.get("path");
            return path != null && path.isTextual() ? path.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
