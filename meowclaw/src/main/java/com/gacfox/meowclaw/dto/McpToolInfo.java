package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * MCP 服务的工具原始信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 工具原始信息")
public class McpToolInfo {
    @Schema(description = "工具名称")
    private String name;
    @Schema(description = "工具说明")
    private String description;
    @Schema(description = "工具入参的 JSON Schema")
    private Map<String, Object> inputSchema;
}
