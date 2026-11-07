package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MCP 工具信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MCP 工具信息")
public class McpToolDTO {
    @Schema(description = "工具名称")
    private String name;
    @Schema(description = "所属 MCP 服务名称")
    private String serviceName;
    @Schema(description = "工具说明")
    private String description;
}
