package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class StatisticsOverviewDto {
    private int modelCount;
    private long totalInputTokens;
    private long totalOutputTokens;
    private long totalMessages;
}
