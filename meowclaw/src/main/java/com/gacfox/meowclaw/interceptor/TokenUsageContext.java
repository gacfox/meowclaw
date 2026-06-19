package com.gacfox.meowclaw.interceptor;

/**
 * 单次会话的Tokens消耗落库上下文，随 {@link TokenUsageLlmInterceptor} 实例传递
 *
 * @param llmId          关联LLM配置ID
 * @param agentId        关联智能体ID
 * @param conversationId 关联会话ID
 * @param batchId        关联批次ID
 * @param model          模型名快照
 */
public record TokenUsageContext(Long llmId, Long agentId, Long conversationId, Long batchId, String model) {
}
