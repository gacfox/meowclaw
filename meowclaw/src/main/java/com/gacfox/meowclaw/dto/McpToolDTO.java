package com.gacfox.meowclaw.dto;

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
public class McpToolDTO {
    private String name;
    private String serviceName;
    private String description;
}
