package com.gacfox.meowclaw.agent.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.McpClientStatusDto;
import com.gacfox.meowclaw.dto.McpToolInfoDto;
import com.gacfox.meowclaw.entity.McpConfig;
import com.gacfox.meowclaw.repository.McpConfigRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapperSupplier;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class McpClientManager implements ApplicationRunner {
    private final Map<String, McpClientState> clientStates = new ConcurrentHashMap<>();
    private final Map<String, McpSyncClient> clients = new ConcurrentHashMap<>();
    private final List<String> initOrder = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final McpConfigRepository mcpConfigRepository;

    public McpClientManager(McpConfigRepository mcpConfigRepository) {
        this.mcpConfigRepository = mcpConfigRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始初始化MCP客户端...");
        initializeAllClients();
    }

    private void initializeAllClients() {
        List<McpConfig> configs = mcpConfigRepository.findAll();
        log.info("找到 {} 个MCP配置待初始化", configs.size());

        for (McpConfig mcpConfig : configs) {
            initializeClient(mcpConfig);
        }

        log.info("MCP客户端初始化完成，已初始化 {} 个客户端", clientStates.size());
    }

    public void initializeClient(McpConfig mcpConfig) {
        String name = mcpConfig.getName();
        log.info("初始化MCP客户端: {}", name);

        McpClientState state = new McpClientState(name);
        clientStates.put(name, state);
        initOrder.add(name);

        try {
            McpSyncClient client = createClient(mcpConfig);
            clients.put(name, client);
            state.setConnected();
            log.info("MCP客户端初始化成功: {}", name);
        } catch (Exception e) {
            log.error("MCP客户端初始化失败: {}", name, e);
            state.setFailed(e.getMessage());
        }
    }

    public void reinitializeClient(McpConfig mcpConfig) {
        String name = mcpConfig.getName();
        log.info("重新初始化MCP客户端: {}", name);

        // 关闭旧的客户端
        McpSyncClient oldClient = clients.remove(name);
        if (oldClient != null) {
            try {
                oldClient.closeGracefully();
            } catch (Exception e) {
                log.warn("关闭旧MCP客户端失败: {}", name, e);
            }
        }

        // 重新初始化
        McpClientState state = new McpClientState(name);
        clientStates.put(name, state);

        try {
            McpSyncClient client = createClient(mcpConfig);
            clients.put(name, client);
            state.setConnected();
            log.info("MCP客户端重新初始化成功: {}", name);
        } catch (Exception e) {
            log.error("MCP客户端重新初始化失败: {}", name, e);
            state.setFailed(e.getMessage());
        }
    }

    private McpSyncClient createClient(McpConfig mcpConfig) {
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
        McpJsonMapper jsonMapper = new JacksonMcpJsonMapperSupplier().get();
        return new StdioClientTransport(params, jsonMapper);
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
        String fullUrl = mcpConfig.getUrl();

        try {
            URI uri = new URI(fullUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();

            String scheme = uri.getScheme();
            String baseUrl = scheme + "://" + host + (port != -1 ? ":" + port : "");
            String endpoint = path + (query != null && !query.isBlank() ? "?" + query : "");

            log.info("解析URL: {} -> baseUrl={}, endpoint={}", fullUrl, baseUrl, endpoint);

            return HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint(endpoint)
                    .openConnectionOnStartup(false)
                    .build();
        } catch (Exception e) {
            log.error("解析URL失败: {}", fullUrl, e);
            return HttpClientStreamableHttpTransport.builder(fullUrl).build();
        }
    }

    private McpClientTransport createSseTransport(McpConfig mcpConfig) {
        String fullUrl = mcpConfig.getUrl();

        try {
            URI uri = new URI(fullUrl);
            String host = uri.getHost();
            int port = uri.getPort();
            String path = uri.getPath();
            String query = uri.getQuery();

            String scheme = uri.getScheme();
            String baseUrl = scheme + "://" + host + (port != -1 ? ":" + port : "");
            String endpoint = path + (query != null && !query.isBlank() ? "?" + query : "");

            log.info("解析SSE URL: {} -> baseUrl={}, endpoint={}", fullUrl, baseUrl, endpoint);

            return HttpClientSseClientTransport.builder(baseUrl)
                    .sseEndpoint(endpoint)
                    .build();
        } catch (Exception e) {
            log.error("解析SSE URL失败: {}", fullUrl, e);
            return HttpClientSseClientTransport.builder(fullUrl).build();
        }
    }

    private List<String> parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(argsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
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
            return objectMapper.readValue(envVarsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {
            });
        } catch (Exception e) {
            log.warn("解析envVars失败: {}", envVarsJson, e);
            return null;
        }
    }

    public McpSyncClient getClient(McpConfig mcpConfig) {
        String key = mcpConfig.getName();
        McpSyncClient client = clients.get(key);

        if (client != null) {
            try {
                return client;
            } catch (Exception e) {
                log.warn("已有MCP客户端连接失效，重新创建: {}", key);
            }
        }

        // 尝试动态初始化
        if (clientStates.get(key) == null) {
            log.warn("MCP客户端不存在: {}", key);
            initializeClient(mcpConfig);
            client = clients.get(key);
            if (client == null) {
                throw new RuntimeException("MCP客户端初始化失败: " + key);
            }
            return client;
        }

        // 重新初始化
        reinitializeClient(mcpConfig);
        client = clients.get(key);
        if (client == null) {
            throw new RuntimeException("MCP客户端重新初始化失败: " + key);
        }
        return client;
    }

    public List<McpToolInfoDto> listToolInfos(McpConfig mcpConfig) {
        McpSyncClient client = getClient(mcpConfig);
        McpSchema.ListToolsResult result = client.listTools();
        List<McpToolInfoDto> tools = new ArrayList<>();
        if (result != null && result.tools() != null) {
            for (var tool : result.tools()) {
                String inputSchemaJson = null;
                if (tool.inputSchema() != null) {
                    try {
                        inputSchemaJson = objectMapper.writeValueAsString(tool.inputSchema());
                    } catch (Exception e) {
                        log.warn("序列化工具输入模式失败: {}", tool.name(), e);
                    }
                }
                tools.add(McpToolInfoDto.builder()
                        .name(tool.name())
                        .description(tool.description())
                        .inputSchema(inputSchemaJson)
                        .build());
            }
        }
        return tools;
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
        McpClientState ignored = clientStates.remove(name);
        McpSyncClient client = clients.remove(name);
        if (client != null) {
            try {
                client.closeGracefully();
                log.info("MCP客户端已关闭: {}", name);
            } catch (Exception e) {
                log.warn("关闭MCP客户端失败: {}", name, e);
            }
        }
        initOrder.remove(name);
    }

    /**
     * 获取所有MCP客户端状态
     */
    public List<McpClientStatusDto> getAllClientStatus() {
        List<McpClientStatusDto> statusList = new ArrayList<>();

        for (String name : initOrder) {
            McpClientState state = clientStates.get(name);
            if (state != null) {
                statusList.add(McpClientStatusDto.builder()
                        .name(state.getName())
                        .status(state.getStatus())
                        .statusLabel(state.getStatusLabel())
                        .errorMessage(state.getErrorMessage())
                        .build());
            }
        }

        return statusList;
    }
}