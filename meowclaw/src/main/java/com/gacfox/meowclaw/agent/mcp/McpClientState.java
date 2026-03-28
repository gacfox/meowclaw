package com.gacfox.meowclaw.agent.mcp;

import com.gacfox.meowclaw.dto.McpClientStatusDto;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * MCP客户端状态
 */
@Data
@AllArgsConstructor
public class McpClientState {
    private final String name;
    private String status;
    private String errorMessage;

    public McpClientState(String name) {
        this.name = name;
        this.status = McpClientStatusDto.STATUS_INITIALIZING;
    }

    public void setConnected() {
        this.status = McpClientStatusDto.STATUS_CONNECTED;
        this.errorMessage = null;
    }

    public void setFailed(String errorMessage) {
        this.status = McpClientStatusDto.STATUS_FAILED;
        this.errorMessage = errorMessage;
    }

    public String getStatusLabel() {
        return switch (status) {
            case McpClientStatusDto.STATUS_INITIALIZING -> McpClientStatusDto.LABEL_INITIALIZING;
            case McpClientStatusDto.STATUS_CONNECTED -> McpClientStatusDto.LABEL_CONNECTED;
            case McpClientStatusDto.STATUS_FAILED -> McpClientStatusDto.LABEL_FAILED;
            default -> status;
        };
    }
}