package com.gacfox.meowclaw.dto;

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
public class McpToolInfo {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}
