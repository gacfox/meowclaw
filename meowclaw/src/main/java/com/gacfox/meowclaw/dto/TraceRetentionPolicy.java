package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "追踪数据留存策略")
public enum TraceRetentionPolicy {
    @Schema(description = "全部留存")
    ALL,
    @Schema(description = "最近一天")
    DAYS_1,
    @Schema(description = "最近三天")
    DAYS_3,
    @Schema(description = "最近七天")
    DAYS_7,
    @Schema(description = "最近一个月")
    MONTH_1,
    @Schema(description = "最近一年")
    YEAR_1
}
