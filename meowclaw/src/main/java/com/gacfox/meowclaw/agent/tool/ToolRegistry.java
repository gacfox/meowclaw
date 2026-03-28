package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.agent.mcp.McpClientManager;
import com.gacfox.meowclaw.agent.mcp.McpToolWrapper;
import com.gacfox.meowclaw.entity.McpConfig;
import com.gacfox.meowclaw.service.McpConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {
    private static final Map<String, Class<? extends Tool>> INTERNAL_TOOLS = new HashMap<>();
    private final Map<String, Tool> toolInstances = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpConfigService mcpConfigService;
    private final McpClientManager mcpClientManager;

    static {
        INTERNAL_TOOLS.put("exec", ExecTool.class);
        INTERNAL_TOOLS.put("read", ReadTool.class);
        INTERNAL_TOOLS.put("write", WriteTool.class);
        INTERNAL_TOOLS.put("edit", EditTool.class);
        INTERNAL_TOOLS.put("todo", TodoTool.class);
        INTERNAL_TOOLS.put("glob", GlobTool.class);
        INTERNAL_TOOLS.put("grep", GrepTool.class);
    }

    public ToolRegistry(McpConfigService mcpConfigService, McpClientManager mcpClientManager) {
        this.mcpConfigService = mcpConfigService;
        this.mcpClientManager = mcpClientManager;
    }

    public List<Tool> loadTools(List<String> toolIds) {
        return toolIds.stream()
                .map(this::getTool)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Tool> parseAndLoadTools(String enabledToolsJson) {
        if (enabledToolsJson == null || enabledToolsJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> toolIds = objectMapper.readValue(enabledToolsJson, new TypeReference<>() {
            });
            return loadTools(toolIds);
        } catch (Exception e) {
            log.error("解析工具配置失败: {}", enabledToolsJson, e);
            return Collections.emptyList();
        }
    }

    public List<Tool> loadToolsWithMcp(String enabledToolsJson, String enabledMcpToolsJson) {
        List<Tool> tools = new ArrayList<>();

        if (enabledToolsJson != null && !enabledToolsJson.isEmpty()) {
            try {
                List<String> toolIds = objectMapper.readValue(enabledToolsJson, new TypeReference<>() {
                });
                tools.addAll(loadTools(toolIds));
            } catch (Exception e) {
                log.error("解析内置工具配置失败: {}", enabledToolsJson, e);
            }
        }

        if (enabledMcpToolsJson != null && !enabledMcpToolsJson.isEmpty()) {
            try {
                List<String> mcpToolIds = objectMapper.readValue(enabledMcpToolsJson, new TypeReference<>() {
                });
                for (String mcpToolId : mcpToolIds) {
                    if (mcpToolId.startsWith("mcp:")) {
                        String serverName = mcpToolId.substring(4);
                        List<Tool> mcpTools = loadMcpTools(serverName);
                        tools.addAll(mcpTools);
                    }
                }
            } catch (Exception e) {
                log.error("解析MCP工具配置失败: {}", enabledMcpToolsJson, e);
            }
        }

        return tools;
    }

    private List<Tool> loadMcpTools(String serverName) {
        try {
            McpConfig mcpConfig = mcpConfigService.findByName(serverName);
            if (mcpConfig == null) {
                log.warn("MCP配置不存在: {}", serverName);
                return List.of();
            }

            List<String> toolNames = mcpClientManager.listToolNames(mcpConfig);
            List<Tool> tools = new ArrayList<>();
            for (String toolName : toolNames) {
                String toolId = "mcp:" + serverName + ":" + toolName;
                if (toolInstances.containsKey(toolId)) {
                    tools.add(toolInstances.get(toolId));
                } else {
                    McpToolWrapper tool = new McpToolWrapper(toolName, serverName, mcpConfig, mcpClientManager);
                    toolInstances.put(toolId, tool);
                    tools.add(tool);
                }
            }
            return tools;
        } catch (Exception e) {
            log.error("加载MCP工具失败: {}", serverName, e);
            return List.of();
        }
    }

    private Tool getTool(String toolId) {
        if (toolInstances.containsKey(toolId)) {
            return toolInstances.get(toolId);
        }

        Class<? extends Tool> toolClass = INTERNAL_TOOLS.get(toolId);
        if (toolClass == null) {
            log.warn("未知工具: {}", toolId);
            return null;
        }

        try {
            Tool tool = toolClass.getDeclaredConstructor().newInstance();
            toolInstances.put(toolId, tool);
            return tool;
        } catch (Exception e) {
            log.error("创建工具实例失败: {}", toolId, e);
            return null;
        }
    }

    public List<Tool> listTools() {
        return INTERNAL_TOOLS.keySet().stream()
                .map(this::getTool)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}