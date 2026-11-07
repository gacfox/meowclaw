package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新 MCP 服务请求")
public class UpdateMcpServiceRequest {
    @Size(max = 100, message = "服务名长度不能超过100")
    @Schema(description = "服务名称")
    private String name;

    @Size(max = 500, message = "描述长度不能超过500")
    @Schema(description = "服务描述")
    private String description;

    @Schema(description = "MCP 通信协议")
    private String protocol;

    @Schema(description = "MCP 协议配置 JSON")
    private String config;
}
