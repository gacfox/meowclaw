package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.converter.McpServiceConverter;
import com.gacfox.meowclaw.dto.CreateMcpServiceRequest;
import com.gacfox.meowclaw.dto.McpServiceDTO;
import com.gacfox.meowclaw.dto.McpServiceTestRequest;
import com.gacfox.meowclaw.dto.McpTestResultDTO;
import com.gacfox.meowclaw.dto.McpToolDTO;
import com.gacfox.meowclaw.dto.McpToolInfo;
import com.gacfox.meowclaw.dto.UpdateMcpServiceRequest;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.McpServiceConfig;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.McpServiceConfigRepository;
import com.gacfox.proarc.agentic.tool.ToolDefinition;
import com.gacfox.proarc.agentic.tool.ToolInvoker;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务管理：CRUD、启用/禁用（动态注册/反注册工具到 ProArc {@link ToolRegistry}）、连接测试、状态刷新。
 * 工具名格式 {@code serviceName__toolName}。
 */
@Slf4j
@Service
public class McpService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INIT_TIMEOUT = Duration.ofSeconds(30);
    private static final String TOOL_NAME_SEPARATOR = "__";
    private static final List<String> PROTOCOLS = List.of("STDIO", "STREAMABLE_HTTP", "SSE");
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("windows");

    private final McpServiceConfigRepository mcpServiceRepository;
    private final AgentRepository agentRepository;
    private final McpServiceConverter converter;
    private final ToolRegistry toolRegistry;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<Long, McpSyncClient> activeClients = new ConcurrentHashMap<>();

    @Autowired
    public McpService(McpServiceConfigRepository mcpServiceRepository,
                      AgentRepository agentRepository,
                      McpServiceConverter converter,
                      ToolRegistry toolRegistry,
                      ThreadPoolTaskScheduler taskScheduler) {
        this.mcpServiceRepository = mcpServiceRepository;
        this.agentRepository = agentRepository;
        this.converter = converter;
        this.toolRegistry = toolRegistry;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    public void init() {
        List<McpServiceConfig> enabled = mcpServiceRepository.findByEnabledTrue();
        for (McpServiceConfig service : enabled) {
            taskScheduler.submit(() -> {
                try {
                    doEnable(service);
                    log.info("MCP service {} connected on startup", service.getName());
                } catch (Exception e) {
                    log.error("MCP service {} failed to connect on startup", service.getName(), e);
                    markError(service, e.getMessage());
                }
            });
        }
    }

    @Transactional(readOnly = true)
    public List<McpServiceDTO> list() {
        return mcpServiceRepository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public McpServiceDTO create(CreateMcpServiceRequest req) {
        validateProtocol(req.getProtocol());
        validateConfig(req.getProtocol(), req.getConfig());
        if (mcpServiceRepository.findByName(req.getName()).isPresent()) {
            throw new IllegalStateException("服务名已存在: " + req.getName());
        }
        McpServiceConfig entity = new McpServiceConfig();
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setProtocol(req.getProtocol());
        entity.setConfig(req.getConfig());
        entity.setEnabled(false);
        entity.setStatus("DISCONNECTED");
        long now = System.currentTimeMillis();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return toDTO(mcpServiceRepository.save(entity));
    }

    @Transactional
    public McpServiceDTO update(Long id, UpdateMcpServiceRequest req) {
        McpServiceConfig entity = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP服务不存在"));

        boolean reconnectNeeded = false;
        if (req.getName() != null && !req.getName().equals(entity.getName())) {
            if (Boolean.TRUE.equals(entity.getEnabled())) {
                throw new IllegalStateException("启用中的服务不能改名，请先禁用");
            }
            if (mcpServiceRepository.findByName(req.getName()).isPresent()) {
                throw new IllegalStateException("服务名已存在: " + req.getName());
            }
            entity.setName(req.getName());
        }
        if (req.getDescription() != null) {
            entity.setDescription(req.getDescription());
        }
        if (req.getProtocol() != null && !req.getProtocol().equals(entity.getProtocol())) {
            validateProtocol(req.getProtocol());
            entity.setProtocol(req.getProtocol());
            reconnectNeeded = true;
        }
        if (req.getConfig() != null && !req.getConfig().equals(entity.getConfig())) {
            validateConfig(entity.getProtocol(), req.getConfig());
            entity.setConfig(req.getConfig());
            reconnectNeeded = true;
        }
        entity.setUpdatedAt(System.currentTimeMillis());
        McpServiceConfig saved = mcpServiceRepository.save(entity);

        if (reconnectNeeded && Boolean.TRUE.equals(saved.getEnabled())) {
            doDisable(saved);
            doEnable(saved);
        }
        return toDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        McpServiceConfig entity = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP服务不存在"));
        checkNotInUse(entity);
        doDisable(entity);
        mcpServiceRepository.delete(entity);
    }

    @Transactional
    public McpServiceDTO toggleEnabled(Long id) {
        McpServiceConfig entity = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP服务不存在"));
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            checkNotInUse(entity);
            doDisable(entity);
        } else {
            doEnable(entity);
        }
        return toDTO(entity);
    }

    /**
     * 测试连接，不持久化、不注册工具
     */
    public McpTestResultDTO test(McpServiceTestRequest req) {
        validateProtocol(req.getProtocol());
        validateConfig(req.getProtocol(), req.getConfig());
        McpServiceConfig temp = new McpServiceConfig();
        temp.setProtocol(req.getProtocol());
        temp.setConfig(req.getConfig());
        try {
            Connection conn = connect(temp);
            try {
                return new McpTestResultDTO(true, null, conn.tools());
            } finally {
                safeClose(conn.client());
            }
        } catch (McpConnectionException e) {
            return new McpTestResultDTO(false, e.getMessage(), List.of());
        }
    }

    @Transactional
    public McpServiceDTO refreshStatus(Long id) {
        McpServiceConfig entity = mcpServiceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP服务不存在"));
        if (Boolean.TRUE.equals(entity.getEnabled())) {
            doDisable(entity);
            doEnable(entity);
        } else {
            try {
                Connection conn = connect(entity);
                entity.setStatus("DISCONNECTED");
                entity.setErrorMessage(null);
                entity.setToolsCache(serializeTools(conn.tools()));
                safeClose(conn.client());
            } catch (McpConnectionException e) {
                entity.setStatus("ERROR");
                entity.setErrorMessage(truncate(e.getMessage(), 1000));
            }
            entity.setLastCheckedAt(System.currentTimeMillis());
            entity.setUpdatedAt(System.currentTimeMillis());
            mcpServiceRepository.save(entity);
        }
        return toDTO(entity);
    }

    /**
     * 列出所有已启用且连接成功的服务下的全部工具（扁平化、含服务名前缀），供智能体配置使用
     */
    @Transactional(readOnly = true)
    public List<McpToolDTO> listEnabledTools() {
        List<McpToolDTO> result = new ArrayList<>();
        for (McpServiceConfig entity : mcpServiceRepository.findByEnabledTrue()) {
            if (!"CONNECTED".equals(entity.getStatus())) continue;
            for (McpToolInfo info : parseToolsCache(entity.getToolsCache())) {
                result.add(new McpToolDTO(entity.getName() + TOOL_NAME_SEPARATOR + info.getName(),
                        entity.getName(), info.getDescription()));
            }
        }
        return result;
    }

    private void doEnable(McpServiceConfig entity) {
        Connection conn;
        try {
            conn = connect(entity);
        } catch (McpConnectionException e) {
            markError(entity, e.getMessage());
            throw new IllegalStateException("MCP连接失败: " + e.getMessage());
        }
        McpSyncClient existing = activeClients.put(entity.getId(), conn.client());
        if (existing != null) safeClose(existing);
        registerTools(entity, conn.client(), conn.tools());
        entity.setEnabled(true);
        entity.setStatus("CONNECTED");
        entity.setErrorMessage(null);
        entity.setToolsCache(serializeTools(conn.tools()));
        entity.setLastCheckedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        mcpServiceRepository.save(entity);
    }

    private void doDisable(McpServiceConfig entity) {
        unregisterTools(entity.getName());
        McpSyncClient client = activeClients.remove(entity.getId());
        if (client != null) safeClose(client);
        entity.setEnabled(false);
        entity.setStatus("DISCONNECTED");
        entity.setUpdatedAt(System.currentTimeMillis());
        mcpServiceRepository.save(entity);
    }

    private void markError(McpServiceConfig entity, String errorMessage) {
        entity.setStatus("ERROR");
        entity.setErrorMessage(truncate(errorMessage, 1000));
        entity.setLastCheckedAt(System.currentTimeMillis());
        entity.setUpdatedAt(System.currentTimeMillis());
        mcpServiceRepository.save(entity);
    }

    private void checkNotInUse(McpServiceConfig entity) {
        String prefix = entity.getName() + TOOL_NAME_SEPARATOR;
        List<String> usedBy = new ArrayList<>();
        for (Agent agent : agentRepository.findAll()) {
            for (String tool : parseStringArray(agent.getEnabledMcpTools())) {
                if (tool.startsWith(prefix)) {
                    usedBy.add(agent.getName());
                    break;
                }
            }
        }
        if (!usedBy.isEmpty()) {
            throw new IllegalStateException("MCP 服务被以下智能体使用，无法操作: " + String.join(", ", usedBy));
        }
    }

    private record Connection(McpSyncClient client, List<McpToolInfo> tools) {}

    private static final class McpConnectionException extends RuntimeException {
        McpConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 建立到 MCP 服务的连接，initialize 后立即 listTools，失败时抛 {@link McpConnectionException}
     */
    private Connection connect(McpServiceConfig service) {
        McpClientTransport transport = buildTransport(service.getProtocol(), service.getConfig());
        McpSyncClient client = McpClient.sync(transport)
                .requestTimeout(REQUEST_TIMEOUT)
                .initializationTimeout(INIT_TIMEOUT)
                .build();
        try {
            client.initialize();
            McpSchema.ListToolsResult result = client.listTools();
            List<McpToolInfo> tools = new ArrayList<>();
            for (McpSchema.Tool tool : result.tools()) {
                tools.add(new McpToolInfo(tool.name(), tool.description(), tool.inputSchema()));
            }
            return new Connection(client, tools);
        } catch (Exception e) {
            safeClose(client);
            throw new McpConnectionException(e.getMessage(), e);
        }
    }

    private McpClientTransport buildTransport(String protocol, String configJson) {
        Map<String, Object> config = parseConfig(configJson);
        return switch (protocol) {
            case "STDIO" -> buildStdioTransport(config);
            case "STREAMABLE_HTTP" -> HttpClientStreamableHttpTransport.builder(getRequiredString(config, "url")).build();
            case "SSE" -> HttpClientSseClientTransport.builder(getRequiredString(config, "url")).build();
            default -> throw new IllegalArgumentException("不支持的协议: " + protocol);
        };
    }

    private McpClientTransport buildStdioTransport(Map<String, Object> config) {
        String command = getRequiredString(config, "command");
        List<String> args = new ArrayList<>();
        Object argsObj = config.get("args");
        if (argsObj instanceof List<?> rawArgs) {
            for (Object a : rawArgs) {
                args.add(String.valueOf(a));
            }
        }

        // Windows 下 npx/npm/node 等需要通过 cmd /c 启动（这些工具实际是 .cmd 批处理脚本）
        ServerParameters.Builder builder;
        if (IS_WINDOWS) {
            List<String> cmdArgs = new ArrayList<>();
            cmdArgs.add("/c");
            cmdArgs.add(command);
            cmdArgs.addAll(args);
            builder = new ServerParameters.Builder("cmd").args(cmdArgs);
        } else {
            builder = new ServerParameters.Builder(command);
            if (!args.isEmpty()) {
                builder.args(args);
            }
        }

        Object envObj = config.get("env");
        if (envObj instanceof Map<?, ?> env) {
            for (Map.Entry<?, ?> entry : env.entrySet()) {
                builder.addEnvVar(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return new StdioClientTransport(builder.build(), McpJsonDefaults.getMapper());
    }

    private void safeClose(McpSyncClient client) {
        try {
            client.closeGracefully();
        } catch (Exception e) {
            log.warn("Failed to close MCP client gracefully", e);
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }

    private Map<String, Object> parseConfig(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("协议配置JSON解析失败: " + e.getMessage(), e);
        }
    }

    private String getRequiredString(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("协议配置缺少必填字段: " + key);
        }
        return String.valueOf(value);
    }

    private void registerTools(McpServiceConfig service, McpSyncClient client, List<McpToolInfo> tools) {
        String prefix = service.getName() + TOOL_NAME_SEPARATOR;
        for (McpToolInfo tool : tools) {
            String fullToolName = prefix + tool.getName();
            unregisterQuietly(fullToolName);
            ToolDefinition toolDef = new ToolDefinition(
                    fullToolName,
                    tool.getDescription(),
                    buildOpenAiToolSchema(fullToolName, tool.getDescription(), tool.getInputSchema()),
                    buildInvoker(client, tool.getName())
            );
            try {
                toolRegistry.register(toolDef);
                log.info("Registered MCP tool: {}", fullToolName);
            } catch (IllegalStateException e) {
                log.warn("Failed to register MCP tool {}: {}", fullToolName, e.getMessage());
            }
        }
    }

    private void unregisterTools(String serviceName) {
        String prefix = serviceName + TOOL_NAME_SEPARATOR;
        for (ToolDefinition def : toolRegistry.getAllTools()) {
            if (def.getToolName().startsWith(prefix)) {
                toolRegistry.unregister(def.getToolName());
                log.info("Unregistered MCP tool: {}", def.getToolName());
            }
        }
    }

    private ToolInvoker buildInvoker(McpSyncClient client, String originalToolName) {
        return (arguments, ctx) -> {
            Map<String, Object> args = parseArguments(arguments);
            McpSchema.CallToolRequest request = new McpSchema.CallToolRequest.Builder()
                    .name(originalToolName)
                    .arguments(args)
                    .build();
            McpSchema.CallToolResult result = client.callTool(request);
            StringBuilder sb = new StringBuilder();
            for (McpSchema.Content content : result.content()) {
                if (content instanceof McpSchema.TextContent text) {
                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(text.text());
                }
            }
            if (Boolean.TRUE.equals(result.isError())) {
                throw new RuntimeException("MCP tool '" + originalToolName + "' returned error: " + sb);
            }
            return sb.toString();
        };
    }

    /**
     * 构造 OpenAI Tool 完整 JSON Schema（与 ProArc 内置 @AgenticTool 生成的格式一致）：
     * {@code {"type":"function","function":{"name":...,"description":...,"parameters":...}}}
     */
    private String buildOpenAiToolSchema(String toolName, String description, Map<String, Object> inputSchema) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", toolName);
        if (description != null && !description.isBlank()) {
            function.put("description", description);
        }
        function.put("parameters", inputSchema != null ? inputSchema : Map.of("type", "object", "properties", Map.of()));
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("type", "function");
        wrapper.put("function", function);
        try {
            return OBJECT_MAPPER.writeValueAsString(wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MCP tool schema: " + toolName, e);
        }
    }

    private void unregisterQuietly(String toolName) {
        if (toolRegistry.getAgenticTool(toolName) != null) {
            toolRegistry.unregister(toolName);
        }
    }

    private McpServiceDTO toDTO(McpServiceConfig entity) {
        McpServiceDTO dto = converter.toDTO(entity);
        dto.setTools(parseToolsCache(entity.getToolsCache()).stream()
                .map(info -> new McpToolDTO(entity.getName() + TOOL_NAME_SEPARATOR + info.getName(),
                        entity.getName(), info.getDescription()))
                .toList());
        return dto;
    }

    private List<McpToolInfo> parseToolsCache(String toolsCache) {
        if (toolsCache == null || toolsCache.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(toolsCache, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse toolsCache for MCP service", e);
            return Collections.emptyList();
        }
    }

    private String serializeTools(List<McpToolInfo> tools) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tools);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MCP tools cache", e);
        }
    }

    private List<String> parseStringArray(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static Map<String, Object> parseArguments(String arguments) throws Exception {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        return OBJECT_MAPPER.readValue(arguments, new TypeReference<>() {});
    }

    private void validateProtocol(String protocol) {
        if (!PROTOCOLS.contains(protocol)) {
            throw new IllegalArgumentException("不支持的协议: " + protocol + "，可选: " + String.join("/", PROTOCOLS));
        }
    }

    private void validateConfig(String protocol, String config) {
        try {
            OBJECT_MAPPER.readTree(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("协议配置不是合法JSON: " + e.getMessage());
        }
        if ("STDIO".equals(protocol) && !config.contains("\"command\"")) {
            throw new IllegalArgumentException("STDIO 协议配置必须包含 command 字段");
        }
        if (("STREAMABLE_HTTP".equals(protocol) || "SSE".equals(protocol)) && !config.contains("\"url\"")) {
            throw new IllegalArgumentException(protocol + " 协议配置必须包含 url 字段");
        }
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() > max ? value.substring(0, max) : value;
    }
}
