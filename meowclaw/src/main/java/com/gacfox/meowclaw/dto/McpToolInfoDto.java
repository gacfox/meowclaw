package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MCP工具信息DTO，包含工具名称、描述和输入参数模式
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class McpToolInfoDto {
    private String name;
    private String description;
    private String inputSchema;
}