package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpClientStatusDto {
    public static final String STATUS_INITIALIZING = "INITIALIZING";
    public static final String STATUS_CONNECTED = "CONNECTED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String LABEL_INITIALIZING = "启动中";
    public static final String LABEL_CONNECTED = "已连接";
    public static final String LABEL_FAILED = "启动失败";

    private String name;
    private String status;
    private String statusLabel;
    private String errorMessage;
}