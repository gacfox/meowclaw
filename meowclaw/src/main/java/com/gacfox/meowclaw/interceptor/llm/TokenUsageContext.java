package com.gacfox.meowclaw.interceptor.llm;

/**
 * 单次LLM调用的落库上下文，随 {@link LlmCallRecordInterceptor} 实例传递
 *
 * @param llmId          关联LLM配置ID
 * @param agentId        关联智能体ID
 * @param conversationId 关联会话ID
 * @param batchId        关联批次ID
 * @param model          模型名快照
 * @param purpose        调用用途：agent主循环/title标题/recap摘要/memory记忆
 */
public record TokenUsageContext(Long llmId, Long agentId, Long conversationId, Long batchId, String model,
                                String purpose) {
}
