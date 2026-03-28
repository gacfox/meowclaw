package com.gacfox.meowclaw.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.entity.McpConfig;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class McpClientManager {
    private final Map<String, McpSyncClient> clientCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();

    public McpSyncClient getClient(McpConfig mcpConfig) {
        String key = mcpConfig.getName();
        McpSyncClient cached = clientCache.get(key);
        if (cached != null) {
            try {
                return cached;
            } catch (Exception e) {
                log.warn("已有MCP客户端连接失效，重新创建: {}", key);
                clientCache.remove(key);
            }
        }

        McpSyncClient client = createClient(mcpConfig);
        clientCache.put(key, client);
        return client;
    }

    private McpSyncClient createClient(McpConfig mcpConfig) {
        try {
            var transport = createTransport(mcpConfig);
            McpSyncClient client = McpClient.sync(transport)
                    .requestTimeout(Duration.ofSeconds(30))
                    .build();
            
            // Windows STDIO模式可能会被版本信息干扰，尝试初始化但允许失败
            try {
                client.initialize();
            } catch (Exception initWarning) {
                log.warn("MCP初始化可能受Windows版本信息干扰，继续尝试: {}", initWarning.getMessage());
            }
            
            log.info("MCP客户端已创建: {}", mcpConfig.getName());
            return client;
        } catch (Exception e) {
            log.error("MCP客户端创建失败: {}", mcpConfig.getName(), e);
            throw new RuntimeException("MCP客户端创建失败: " + e.getMessage(), e);
        }
    }

    private McpClientTransport createTransport(McpConfig mcpConfig) {
        String transportType = mcpConfig.getTransportType();
        log.info("创建MCP传输: type={}, name={}", transportType, mcpConfig.getName());

        return switch (transportType) {
            case "stdio" -> createFilteredStdioTransport(mcpConfig);
            case "streamable_http" -> createStreamableHttpTransport(mcpConfig);
            case "sse" -> createSseTransport(mcpConfig);
            default -> throw new IllegalArgumentException("不支持的传输类型: " + transportType);
        };
    }

    private McpClientTransport createFilteredStdioTransport(McpConfig mcpConfig) {
        String command = mcpConfig.getCommand();
        String resolvedCommand = resolveCommandPath(command);
        
        List<String> args = parseArgs(mcpConfig.getArgs());
        Map<String, String> envVars = parseEnvVars(mcpConfig.getEnvVars());
        
        ServerParameters.Builder builder = ServerParameters.builder(resolvedCommand);
        if (args != null && !args.isEmpty()) {
            builder.args(args.toArray(new String[0]));
        }
        if (envVars != null && !envVars.isEmpty()) {
            builder.env(envVars);
        }
        
        ServerParameters params = builder.build();
        StdioClientTransport transport = new StdioClientTransport(params, jsonMapper);
        
        return transport;
    }

    private String resolveCommandPath(String command) {
        String os = System.getProperty("os.name").toLowerCase();
        
        if (os.contains("win")) {
            // Windows: 使用 where 查找，添加 .cmd 后缀
            try {
                ProcessBuilder pb = new ProcessBuilder("where", command);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                    String line = reader.readLine();
                    if (line != null && !line.isBlank()) {
                        String resolved = line.trim();
                        if (!resolved.toLowerCase().endsWith(".cmd") && !resolved.toLowerCase().endsWith(".exe")) {
                            resolved = resolved + ".cmd";
                        }
                        log.info("解析命令 {} -> {}", command, resolved);
                        return resolved;
                    }
                }
            } catch (Exception e) {
                log.debug("查找命令路径失败: {}", command, e);
            }
            // 回退：添加 .cmd
            return command + ".cmd";
        } else {
            // Linux/Mac: 使用 which
            try {
                ProcessBuilder pb = new ProcessBuilder("which", command);
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(process.getInputStream()));
                    String line = reader.readLine();
                    if (line != null && !line.isBlank()) {
                        log.info("解析命令 {} -> {}", command, line.trim());
                        return line.trim();
                    }
                }
            } catch (Exception e) {
                log.debug("查找命令路径失败: {}", command, e);
            }
            return command;
        }
    }

    private McpClientTransport createStreamableHttpTransport(McpConfig mcpConfig) {
        return HttpClientStreamableHttpTransport.builder(mcpConfig.getUrl()).build();
    }

    private McpClientTransport createSseTransport(McpConfig mcpConfig) {
        return HttpClientSseClientTransport.builder(mcpConfig.getUrl()).build();
    }

    private List<String> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(argsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析args失败: {}", argsJson, e);
            return null;
        }
    }

    private Map<String, String> parseEnvVars(String envVarsJson) {
        if (envVarsJson == null || envVarsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(envVarsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析envVars失败: {}", envVarsJson, e);
            return null;
        }
    }

    public List<String> listToolNames(McpConfig mcpConfig) {
        McpSyncClient client = getClient(mcpConfig);
        McpSchema.ListToolsResult result = client.listTools();
        List<String> names = new ArrayList<>();
        if (result != null && result.tools() != null) {
            for (var tool : result.tools()) {
                names.add(tool.name());
            }
        }
        return names;
    }

    public String callTool(McpConfig mcpConfig, String toolName, Map<String, Object> args) {
        McpSyncClient client = getClient(mcpConfig);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, args);
        McpSchema.CallToolResult result = client.callTool(request);

        if (result == null) {
            return "{\"error\": \"tool result is null\"}";
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    public void removeClient(String name) {
        McpSyncClient client = clientCache.remove(name);
        if (client != null) {
            try {
                client.closeGracefully();
                log.info("MCP客户端已关闭: {}", name);
            } catch (Exception e) {
                log.warn("关闭MCP客户端失败: {}", name, e);
            }
        }
    }

    public void removeClient(McpConfig mcpConfig) {
        removeClient(mcpConfig.getName());
    }
}