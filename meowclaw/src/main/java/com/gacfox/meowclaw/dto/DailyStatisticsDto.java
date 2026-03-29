package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class DailyStatisticsDto {
    private String date;
    private String apiUrl;
    private String model;
    private String displayName;
    private long inputTokens;
    private long outputTokens;
    private long messageCount;
}
