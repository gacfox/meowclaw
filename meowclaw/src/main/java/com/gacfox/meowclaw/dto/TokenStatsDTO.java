package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

/**
 * Tokens统计报表响应
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Token 用量统计")
public class TokenStatsDTO {
    @Schema(description = "Token 用量汇总")
    private TokenStatsSummary summary;
    @Schema(description = "按调用量排名的模型列表")
    private List<TokenTopModel> topModels;
    @Schema(description = "统计日期列表")
    private List<String> dates;
    @Schema(description = "各模型的按日统计序列")
    private List<TokenModelSeries> modelSeries;

    /**
     * 筛选条件下Tokens汇总
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "Token 用量汇总")
    public static class TokenStatsSummary {
        @Schema(description = "总输入 Token 数")
        private long totalInputTokens;
        @Schema(description = "总输出 Token 数")
        private long totalOutputTokens;
        @Schema(description = "总 Token 数")
        private long totalTokens;
        @Schema(description = "缓存输入 Token 总数，没有已上报调用时为 null")
        private Long totalCachedTokens;
        @Schema(description = "缓存命中率（缓存输入 Token / 已上报调用的输入 Token），无法计算时为 null")
        private Double cacheHitRate;
        @Schema(description = "调用次数")
        private long callCount;
    }

    /**
     * 按调用量排名的Top模型
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "按调用量排名的模型")
    public static class TokenTopModel {
        @Schema(description = "语言模型配置 ID")
        private long llmId;
        @Schema(description = "语言模型配置名称")
        private String llmName;
        @Schema(description = "模型名称")
        private String model;
        @Schema(description = "调用次数")
        private long callCount;
        @Schema(description = "输入 Token 数")
        private long inputTokens;
        @Schema(description = "输出 Token 数")
        private long outputTokens;
    }

    /**
     * 单个模型的按日序列，数组与 {@link #dates} 对齐
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "单个模型的按日统计序列")
    public static class TokenModelSeries {
        @Schema(description = "语言模型配置 ID")
        private long llmId;
        @Schema(description = "语言模型配置名称")
        private String llmName;
        @Schema(description = "模型名称")
        private String model;
        @Schema(description = "各日期的输入 Token 数")
        private long[] input;
        @Schema(description = "各日期的输出 Token 数")
        private long[] output;
        @Schema(description = "各日期的总 Token 数")
        private long[] total;
        @Schema(description = "各日期的调用次数")
        private long[] callCount;
    }
}
