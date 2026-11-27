package com.gacfox.meowclaw.interceptor.llm;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 单次LLM调用的落库上下文，随 {@link LlmCallRecordInterceptor} 实例传递。
 */
@Getter
@AllArgsConstructor
public class TokenUsageContext {
    /** 关联LLM配置ID */
    private final Long llmId;
    /** 关联智能体ID */
    private final Long agentId;
    /** 关联会话ID */
    private final Long conversationId;
    /** 关联批次ID */
    private final Long batchId;
    /** 模型名快照 */
    private final String model;
    /** 调用用途：agent主循环/title标题/recap摘要/memory记忆 */
    private final String purpose;
}
