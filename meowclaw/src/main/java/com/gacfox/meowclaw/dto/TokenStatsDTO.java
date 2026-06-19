package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Tokens统计报表响应
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenStatsDTO {

    private TokenStatsSummary summary;
    private List<TokenTopModel> topModels;
    private List<String> dates;
    private List<TokenModelSeries> modelSeries;

    /**
     * 筛选条件下Tokens汇总
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TokenStatsSummary {
        private long totalInputTokens;
        private long totalOutputTokens;
        private long totalTokens;
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
    public static class TokenTopModel {
        private long llmId;
        private String llmName;
        private String model;
        private long callCount;
        private long inputTokens;
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
    public static class TokenModelSeries {
        private long llmId;
        private String llmName;
        private String model;
        private long[] input;
        private long[] output;
        private long[] total;
        private long[] callCount;
    }
}
