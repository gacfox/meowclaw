package com.gacfox.meowclaw.dto;

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
public class McpTestResultDTO {
    private boolean success;
    private String errorMessage;
    private List<McpToolInfo> tools;
}
