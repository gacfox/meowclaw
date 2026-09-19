package com.gacfox.meowclaw.dto;

/**
 * Tokens统计投影行：仅含统计所需列，避免加载调用记录的大字段
 */
public record TokenUsageStatsRow(Long llmId, String model, Long inputTokens, Long outputTokens,
                                 Long totalTokens, Long createdAt) {
}
