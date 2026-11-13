package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "服务端系统资源占用信息")
public class SystemResourceMetricsDTO {
    @Schema(description = "系统 CPU 使用率百分比（0-100），不可用时为 null")
    private final Double cpuUsage;

    @Schema(description = "系统内存使用率百分比（0-100），不可用时为 null")
    private final Double memoryUsage;

    @Schema(description = "采集时间戳")
    private final long timestamp;
}
