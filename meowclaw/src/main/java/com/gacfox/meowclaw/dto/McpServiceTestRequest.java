package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 测试连接请求（不持久化）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 服务连接测试请求")
public class McpServiceTestRequest {
    @NotBlank(message = "协议不能为空")
    @Schema(description = "MCP 通信协议")
    private String protocol;

    @NotBlank(message = "协议配置不能为空")
    @Schema(description = "MCP 协议配置 JSON")
    private String config;
}
