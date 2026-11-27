package com.gacfox.meowclaw.dto.observability;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tokens统计投影行：仅含统计所需列，避免加载调用记录的大字段
 */
@Getter
@AllArgsConstructor
@Schema(description = "Token 用量统计行")
public class TokenUsageStatsRow {
    @Schema(description = "语言模型配置 ID")
    private final Long llmId;
    @Schema(description = "模型名称")
    private final String model;
    @Schema(description = "输入 Token 数")
    private final Long inputTokens;
    @Schema(description = "输出 Token 数")
    private final Long outputTokens;
    @Schema(description = "总 Token 数")
    private final Long totalTokens;
    @Schema(description = "缓存输入 Token 数，未上报时为 null")
    private final Long cachedTokens;
    @Schema(description = "调用时间戳")
    private final Long createdAt;
}
