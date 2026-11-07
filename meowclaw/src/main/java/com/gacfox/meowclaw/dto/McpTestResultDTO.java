package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * MCP 测试连接结果
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 服务连接测试结果")
public class McpTestResultDTO {
    @Schema(description = "是否测试成功")
    private boolean success;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "检测到的 MCP 工具列表")
    private List<McpToolInfo> tools;
}
