package com.gacfox.meowclaw.agent.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.agent.tool.Tool;
import com.gacfox.meowclaw.agent.tool.ToolExecutionContext;
import com.gacfox.meowclaw.entity.McpConfig;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
public class McpToolWrapper implements Tool {
    private final String toolName;
    private final String mcpServerName;
    private final McpConfig mcpConfig;
    private final McpClientManager clientManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public McpToolWrapper(String toolName, String mcpServerName, McpConfig mcpConfig, McpClientManager clientManager) {
        this.toolName = toolName;
        this.mcpServerName = mcpServerName;
        this.mcpConfig = mcpConfig;
        this.clientManager = clientManager;
    }

    @Override
    public String getName() {
        return "mcp:" + mcpServerName + ":" + toolName;
    }

    @Override
    public String getDescription() {
        return "MCP工具 [" + mcpServerName + "] - " + toolName;
    }

    @Override
    public String getParameters() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public Mono<String> execute(String params, ToolExecutionContext context) {
        return Mono.fromCallable(() -> {
            Map<String, Object> args;
            if (params == null || params.isBlank()) {
                args = Map.of();
            } else {
                args = objectMapper.readValue(params, new TypeReference<>() {});
            }
            return clientManager.callTool(mcpConfig, toolName, args);
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }
}