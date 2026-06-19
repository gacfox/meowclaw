package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MCP 工具信息（已含服务名前缀）
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpToolDTO {
    /** 完整工具名（格式：serviceName__toolName） */
    private String name;
    /** 所属服务名 */
    private String serviceName;
    /** 工具描述 */
    private String description;
}
