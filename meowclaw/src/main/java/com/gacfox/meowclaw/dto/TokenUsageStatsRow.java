package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Tokens统计投影行：仅含统计所需列，避免加载调用记录的大字段
 */
@Schema(description = "Token 用量统计行")
public record TokenUsageStatsRow(
        @Schema(description = "语言模型配置 ID")
        Long llmId,
        @Schema(description = "模型名称")
        String model,
        @Schema(description = "输入 Token 数")
        Long inputTokens,
        @Schema(description = "输出 Token 数")
        Long outputTokens,
        @Schema(description = "总 Token 数")
        Long totalTokens,
        @Schema(description = "缓存输入 Token 数，未上报时为 null")
        Long cachedTokens,
        @Schema(description = "调用时间戳")
        Long createdAt) {
}
