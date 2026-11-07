package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 服务配置与状态")
public class McpServiceDTO {
    @Schema(description = "MCP 服务 ID")
    private Long id;
    @Schema(description = "服务名称")
    private String name;
    @Schema(description = "服务描述")
    private String description;
    @Schema(description = "MCP 通信协议")
    private String protocol;
    @Schema(description = "MCP 协议配置 JSON")
    private String config;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "MCP 服务连接状态")
    private String status;
    @Schema(description = "MCP 工具列表")
    private List<McpToolDTO> tools;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "最近检查时间戳")
    private Long lastCheckedAt;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
