package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class McpConfigDto {
    private Long id;
    @NotBlank(message = "MCP配置名称不能为空")
    private String name;
    @NotBlank(message = "传输类型不能为空")
    private String transportType;
    private String command;
    private String args;
    private String envVars;
    private String url;
}