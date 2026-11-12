package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.gacfox.meowclaw.dto.AgentConfigBundleDTO;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.ConfigImportResultDTO;
import com.gacfox.meowclaw.dto.EmbeddingModelDTO;
import com.gacfox.meowclaw.dto.EnvironmentVariableDTO;
import com.gacfox.meowclaw.dto.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.McpServiceDTO;
import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.entity.EmbeddingModel;
import com.gacfox.meowclaw.entity.EnvironmentVariable;
import com.gacfox.meowclaw.entity.GuardrailPolicy;
import com.gacfox.meowclaw.entity.Llm;
import com.gacfox.meowclaw.entity.McpServiceConfig;
import com.gacfox.meowclaw.entity.ScheduledTask;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.repository.EmbeddingModelRepository;
import com.gacfox.meowclaw.repository.EnvironmentVariableRepository;
import com.gacfox.meowclaw.repository.GuardrailPolicyRepository;
import com.gacfox.meowclaw.repository.LlmRepository;
import com.gacfox.meowclaw.repository.McpServiceConfigRepository;
import com.gacfox.meowclaw.repository.ScheduledTaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class AgentConfigTransferService {
    public static final int FORMAT_VERSION = 1;
    private static final long MAX_IMPORT_SIZE = 50L * 1024 * 1024;
    private static final int UNIQUE_WRITE_ATTEMPTS = 5;

    private static final String LLM = "LLM";
    private static final String EMBEDDING_MODEL = "EMBEDDING_MODEL";
    private static final String MCP_SERVICE = "MCP_SERVICE";
    private static final String GUARDRAIL_POLICY = "GUARDRAIL_POLICY";
    private static final String AGENT = "AGENT";
    private static final String ENVIRONMENT_VARIABLE = "ENVIRONMENT_VARIABLE";
    private static final String SCHEDULED_TASK = "SCHEDULED_TASK";

    private final LlmRepository llmRepository;
    private final EmbeddingModelRepository embeddingModelRepository;
    private final McpServiceConfigRepository mcpServiceRepository;
    private final GuardrailPolicyRepository guardrailPolicyRepository;
    private final AgentRepository agentRepository;
    private final EnvironmentVariableRepository environmentVariableRepository;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final AgentConfigImportItemService importItemService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AgentConfigTransferService(LlmRepository llmRepository,
                                      EmbeddingModelRepository embeddingModelRepository,
                                      McpServiceConfigRepository mcpServiceRepository,
                                      GuardrailPolicyRepository guardrailPolicyRepository,
                                      AgentRepository agentRepository,
                                      EnvironmentVariableRepository environmentVariableRepository,
                                      ScheduledTaskRepository scheduledTaskRepository,
                                      AgentConfigImportItemService importItemService,
                                      ObjectMapper objectMapper) {
        this.llmRepository = llmRepository;
        this.embeddingModelRepository = embeddingModelRepository;
        this.mcpServiceRepository = mcpServiceRepository;
        this.guardrailPolicyRepository = guardrailPolicyRepository;
        this.agentRepository = agentRepository;
        this.environmentVariableRepository = environmentVariableRepository;
        this.scheduledTaskRepository = scheduledTaskRepository;
        this.importItemService = importItemService;
        this.objectMapper = objectMapper;
    }

    /**
     * 导出全部智能体相关配置，不包含运行时状态和运行记录
     *
     * @return JSON 配置文件内容
     */
    @Transactional(readOnly = true)
    public byte[] exportConfig() {
        AgentConfigBundleDTO bundle = new AgentConfigBundleDTO();
        bundle.setFormatVersion(FORMAT_VERSION);
        bundle.setExportedAt(System.currentTimeMillis());
        bundle.setLlms(llmRepository.findAll().stream().map(this::exportLlm).toList());
        bundle.setEmbeddingModels(embeddingModelRepository.findAll().stream().map(this::exportEmbeddingModel).toList());
        bundle.setMcpServices(mcpServiceRepository.findAll().stream().map(this::exportMcpService).toList());
        bundle.setGuardrailPolicies(guardrailPolicyRepository.findAll().stream().map(this::exportGuardrailPolicy).toList());
        bundle.setAgents(agentRepository.findAll().stream().map(this::exportAgent).toList());
        bundle.setEnvironmentVariables(environmentVariableRepository.findAll().stream().map(this::exportEnvironmentVariable).toList());
        bundle.setScheduledTasks(scheduledTaskRepository.findAll().stream().map(this::exportScheduledTask).toList());
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(bundle);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("生成配置导出文件失败", e);
        }
    }

    /**
     * 校验配置包并逐项导入，单项失败不影响其他配置
     *
     * @param file 配置 JSON 文件
     * @return 分类统计和逐项导入结果
     */
    public ConfigImportResultDTO importConfig(MultipartFile file) {
        AgentConfigBundleDTO bundle = readBundle(file);
        validateBundle(bundle);
        Set<String> reservedMcpNames = new HashSet<>();
        Set<String> reservedGuardrailNames = new HashSet<>();

        ConfigImportResultDTO result = new ConfigImportResultDTO();
        Map<Long, Long> llmIds = importLlms(bundle.getLlms(), result);
        Map<Long, Long> embeddingModelIds = importEmbeddingModels(bundle.getEmbeddingModels(), result);
        Map<String, String> mcpNames = importMcpServices(bundle.getMcpServices(), reservedMcpNames, result);
        Set<String> exportedMcpNames = bundle.getMcpServices().stream()
                .filter(Objects::nonNull)
                .map(AgentConfigBundleDTO.McpServiceData::getName)
                .collect(Collectors.toSet());
        importGuardrailPolicies(bundle.getGuardrailPolicies(), exportedMcpNames, mcpNames,
                reservedGuardrailNames, result);
        Map<Long, Long> agentIds = importAgents(bundle.getAgents(), llmIds, embeddingModelIds,
                exportedMcpNames, mcpNames, result);
        importEnvironmentVariables(bundle.getEnvironmentVariables(), agentIds, result);
        importScheduledTasks(bundle.getScheduledTasks(), agentIds, result);
        return result;
    }

    private AgentConfigBundleDTO readBundle(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择非空的配置文件");
        }
        if (file.getSize() > MAX_IMPORT_SIZE) {
            throw new IllegalArgumentException("配置文件不能超过50MB");
        }
        try {
            return objectMapper.readValue(file.getBytes(), AgentConfigBundleDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("配置文件格式无效，请选择由本系统导出的 JSON 文件", e);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取配置文件失败", e);
        }
    }

    private void validateBundle(AgentConfigBundleDTO bundle) {
        if (bundle == null) {
            throw new IllegalArgumentException("配置文件内容不能为空");
        }
        if (bundle.getFormatVersion() == null || bundle.getFormatVersion() != FORMAT_VERSION) {
            throw new IllegalArgumentException("不支持的配置文件版本");
        }
        requireList(bundle.getLlms(), "llms");
        requireList(bundle.getEmbeddingModels(), "embeddingModels");
        requireList(bundle.getMcpServices(), "mcpServices");
        requireList(bundle.getGuardrailPolicies(), "guardrailPolicies");
        requireList(bundle.getAgents(), "agents");
        requireList(bundle.getEnvironmentVariables(), "environmentVariables");
        requireList(bundle.getScheduledTasks(), "scheduledTasks");
        requireUniqueSourceIds(bundle.getLlms().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.LlmData::getSourceId).toList(), "大语言模型");
        requireUniqueSourceIds(bundle.getEmbeddingModels().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.EmbeddingModelData::getSourceId).toList(), "向量嵌入模型");
        requireUniqueSourceIds(bundle.getMcpServices().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.McpServiceData::getSourceId).toList(), "MCP 服务");
        requireUniqueSourceIds(bundle.getGuardrailPolicies().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.GuardrailPolicyData::getSourceId).toList(), "护栏策略");
        requireUniqueSourceIds(bundle.getAgents().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.AgentData::getSourceId).toList(), "智能体");
        requireUniqueSourceIds(bundle.getEnvironmentVariables().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.EnvironmentVariableData::getSourceId).toList(), "环境变量");
        requireUniqueSourceIds(bundle.getScheduledTasks().stream().filter(Objects::nonNull).map(AgentConfigBundleDTO.ScheduledTaskData::getSourceId).toList(), "定时任务");
    }

    private void requireList(List<?> list, String field) {
        if (list == null) {
            throw new IllegalArgumentException("配置文件缺少 " + field + " 数组");
        }
    }

    private void requireUniqueSourceIds(List<Long> sourceIds, String type) {
        Set<Long> ids = new HashSet<>();
        for (Long sourceId : sourceIds) {
            if (sourceId != null && !ids.add(sourceId)) {
                throw new IllegalArgumentException(type + "存在重复的 sourceId，无法安全恢复关联");
            }
        }
    }

    private Map<Long, Long> importLlms(List<AgentConfigBundleDTO.LlmData> items, ConfigImportResultDTO result) {
        Map<Long, Long> idMap = new HashMap<>();
        for (AgentConfigBundleDTO.LlmData data : items) {
            if (data == null) {
                recordFailure(result, LLM, null, null, "配置项不能为空");
                continue;
            }
            try {
                LlmDTO created = importItemService.createLlm(data);
                if (data.getSourceId() != null) {
                    idMap.put(data.getSourceId(), created.getId());
                }
                recordSuccess(result, LLM, data.getSourceId(), data.getName(), created.getId(), created.getName(), null);
            } catch (Exception e) {
                recordFailure(result, LLM, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
        return idMap;
    }

    private Map<Long, Long> importEmbeddingModels(List<AgentConfigBundleDTO.EmbeddingModelData> items,
                                                   ConfigImportResultDTO result) {
        Map<Long, Long> idMap = new HashMap<>();
        for (AgentConfigBundleDTO.EmbeddingModelData data : items) {
            if (data == null) {
                recordFailure(result, EMBEDDING_MODEL, null, null, "配置项不能为空");
                continue;
            }
            try {
                EmbeddingModelDTO created = importItemService.createEmbeddingModel(data);
                if (data.getSourceId() != null) {
                    idMap.put(data.getSourceId(), created.getId());
                }
                recordSuccess(result, EMBEDDING_MODEL, data.getSourceId(), data.getName(), created.getId(),
                        created.getName(), null);
            } catch (Exception e) {
                recordFailure(result, EMBEDDING_MODEL, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
        return idMap;
    }

    private Map<String, String> importMcpServices(List<AgentConfigBundleDTO.McpServiceData> items,
                                                   Set<String> reservedMcpNames,
                                                   ConfigImportResultDTO result) {
        Map<String, String> nameMap = new LinkedHashMap<>();
        for (AgentConfigBundleDTO.McpServiceData data : items) {
            if (data == null) {
                recordFailure(result, MCP_SERVICE, null, null, "配置项不能为空");
                continue;
            }
            try {
                McpServiceDTO created = createMcpWithUniqueName(data, reservedMcpNames);
                nameMap.putIfAbsent(data.getName(), created.getName());
                String message = Boolean.TRUE.equals(data.getEnabled()) ? "已按安全策略保持禁用" : null;
                recordSuccess(result, MCP_SERVICE, data.getSourceId(), data.getName(), created.getId(),
                        created.getName(), message);
            } catch (Exception e) {
                recordFailure(result, MCP_SERVICE, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
        return nameMap;
    }

    private McpServiceDTO createMcpWithUniqueName(AgentConfigBundleDTO.McpServiceData data,
                                                   Set<String> reservedMcpNames) {
        for (int attempt = 0; attempt < UNIQUE_WRITE_ATTEMPTS; attempt++) {
            String finalName = allocateName(data.getName(), 100,
                    name -> mcpServiceRepository.findByName(name).isPresent(), reservedMcpNames);
            try {
                return importItemService.createMcpService(data, finalName);
            } catch (DataIntegrityViolationException e) {
                if (attempt == UNIQUE_WRITE_ATTEMPTS - 1) {
                    throw new IllegalArgumentException("服务名称持续冲突，无法导入", e);
                }
            }
        }
        throw new IllegalStateException("无法分配 MCP 服务名称");
    }

    private void importGuardrailPolicies(List<AgentConfigBundleDTO.GuardrailPolicyData> items,
                                         Set<String> exportedMcpNames, Map<String, String> mcpNames,
                                         Set<String> reservedGuardrailNames,
                                         ConfigImportResultDTO result) {
        for (AgentConfigBundleDTO.GuardrailPolicyData data : items) {
            if (data == null) {
                recordFailure(result, GUARDRAIL_POLICY, null, null, "配置项不能为空");
                continue;
            }
            try {
                TextRewrite rewrite = rewriteGuardrailTools(data.getConfigJson(), exportedMcpNames, mcpNames);
                GuardrailPolicyDTO created = createGuardrailWithUniqueName(data, rewrite.text(), reservedGuardrailNames);
                String message = rewrite.missingReference() ? "部分 MCP 服务未能导入，相关工具保留原名称" : null;
                recordSuccess(result, GUARDRAIL_POLICY, data.getSourceId(), data.getName(), created.getId(),
                        created.getName(), message);
            } catch (Exception e) {
                recordFailure(result, GUARDRAIL_POLICY, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
    }

    private GuardrailPolicyDTO createGuardrailWithUniqueName(AgentConfigBundleDTO.GuardrailPolicyData data,
                                                              String configJson,
                                                              Set<String> reservedGuardrailNames) {
        String originalName = data.getName() == null ? null : data.getName().trim();
        for (int attempt = 0; attempt < UNIQUE_WRITE_ATTEMPTS; attempt++) {
            String finalName = allocateName(originalName, 100, guardrailPolicyRepository::existsByName,
                    reservedGuardrailNames);
            try {
                return importItemService.createGuardrailPolicy(data, finalName, configJson);
            } catch (DataIntegrityViolationException e) {
                if (attempt == UNIQUE_WRITE_ATTEMPTS - 1) {
                    throw new IllegalArgumentException("策略名称持续冲突，无法导入", e);
                }
            }
        }
        throw new IllegalStateException("无法分配护栏策略名称");
    }

    private Map<Long, Long> importAgents(List<AgentConfigBundleDTO.AgentData> items, Map<Long, Long> llmIds,
                                         Map<Long, Long> embeddingModelIds, Set<String> exportedMcpNames,
                                         Map<String, String> mcpNames, ConfigImportResultDTO result) {
        Map<Long, Long> idMap = new HashMap<>();
        for (AgentConfigBundleDTO.AgentData data : items) {
            if (data == null) {
                recordFailure(result, AGENT, null, null, "配置项不能为空");
                continue;
            }
            Long llmId = llmIds.get(data.getLlmId());
            Long secondaryLlmId = llmIds.get(data.getSecondaryLlmId());
            if (llmId == null || secondaryLlmId == null) {
                recordSkipped(result, AGENT, data.getSourceId(), data.getName(), "主模型或辅助模型未能导入，无法恢复必需关联");
                continue;
            }
            Long embeddingModelId = null;
            String message = null;
            if (data.getEmbeddingModelId() != null) {
                embeddingModelId = embeddingModelIds.get(data.getEmbeddingModelId());
                if (embeddingModelId == null) {
                    message = "向量模型未能导入，已清除该关联";
                }
            }
            try {
                String enabledTools = validateStringArray(data.getEnabledTools(), "内置工具配置");
                TextRewrite rewrite = rewriteAgentMcpTools(data.getEnabledMcpTools(), exportedMcpNames, mcpNames);
                if (rewrite.missingReference()) {
                    message = appendMessage(message, "部分 MCP 服务未能导入，相关工具保留原名称");
                }
                AgentDTO created = importItemService.createAgent(data, llmId, secondaryLlmId,
                        embeddingModelId, enabledTools, rewrite.text());
                if (data.getSourceId() != null) {
                    idMap.put(data.getSourceId(), created.getId());
                }
                recordSuccess(result, AGENT, data.getSourceId(), data.getName(), created.getId(),
                        created.getName(), appendMessage(message, "已使用默认工作区，头像与工作区路径未迁移"));
            } catch (Exception e) {
                recordFailure(result, AGENT, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
        return idMap;
    }

    private void importEnvironmentVariables(List<AgentConfigBundleDTO.EnvironmentVariableData> items,
                                             Map<Long, Long> agentIds, ConfigImportResultDTO result) {
        for (AgentConfigBundleDTO.EnvironmentVariableData data : items) {
            if (data == null) {
                recordFailure(result, ENVIRONMENT_VARIABLE, null, null, "配置项不能为空");
                continue;
            }
            Long agentId = null;
            if (data.getAgentId() != null) {
                agentId = agentIds.get(data.getAgentId());
                if (agentId == null) {
                    recordSkipped(result, ENVIRONMENT_VARIABLE, data.getSourceId(), data.getKey(),
                            "关联智能体未能导入");
                    continue;
                }
            }
            if (environmentExists(agentId, data.getKey())) {
                recordSkipped(result, ENVIRONMENT_VARIABLE, data.getSourceId(), data.getKey(),
                        "当前作用域已存在同名环境变量，未覆盖原值");
                continue;
            }
            try {
                EnvironmentVariableDTO created = importItemService.createEnvironmentVariable(agentId,
                        data.getKey(), data.getValue());
                recordSuccess(result, ENVIRONMENT_VARIABLE, data.getSourceId(), data.getKey(), created.getId(),
                        created.getKey(), null);
            } catch (Exception e) {
                recordFailure(result, ENVIRONMENT_VARIABLE, data.getSourceId(), data.getKey(), errorMessage(e));
            }
        }
    }

    private boolean environmentExists(Long agentId, String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim();
        return agentId == null
                ? environmentVariableRepository.findByAgentIdIsNullAndKeyNameIgnoreCase(normalized).isPresent()
                : environmentVariableRepository.findByAgentIdAndKeyNameIgnoreCase(agentId, normalized).isPresent();
    }

    private void importScheduledTasks(List<AgentConfigBundleDTO.ScheduledTaskData> items,
                                      Map<Long, Long> agentIds, ConfigImportResultDTO result) {
        for (AgentConfigBundleDTO.ScheduledTaskData data : items) {
            if (data == null) {
                recordFailure(result, SCHEDULED_TASK, null, null, "配置项不能为空");
                continue;
            }
            Long agentId = agentIds.get(data.getAgentId());
            if (agentId == null) {
                recordSkipped(result, SCHEDULED_TASK, data.getSourceId(), data.getName(), "关联智能体未能导入");
                continue;
            }
            try {
                ScheduledTaskDTO created = importItemService.createScheduledTask(data, agentId);
                String message = Boolean.TRUE.equals(data.getEnabled()) ? "已按安全策略保持禁用" : null;
                recordSuccess(result, SCHEDULED_TASK, data.getSourceId(), data.getName(), created.getId(),
                        created.getName(), message);
            } catch (Exception e) {
                recordFailure(result, SCHEDULED_TASK, data.getSourceId(), data.getName(), errorMessage(e));
            }
        }
    }

    private String allocateName(String originalName, int maxLength, Predicate<String> exists, Set<String> reserved) {
        if (originalName == null || originalName.isBlank()) {
            return originalName;
        }
        if (!exists.test(originalName) && reserved.add(originalName)) {
            return originalName;
        }
        for (int index = 1; index < 1000; index++) {
            String suffix = index == 1 ? " (导入)" : " (导入 " + index + ")";
            String base = originalName.substring(0, Math.min(originalName.length(), maxLength - suffix.length()));
            String candidate = base + suffix;
            if (!exists.test(candidate) && reserved.add(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("无法为配置分配唯一名称");
    }

    private String validateStringArray(String json, String field) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                throw new IllegalArgumentException(field + "必须是 JSON 字符串数组");
            }
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException(field + "必须是 JSON 字符串数组");
                }
            }
            return json;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(field + "不是有效的 JSON", e);
        }
    }

    private TextRewrite rewriteAgentMcpTools(String json, Set<String> exportedMcpNames,
                                             Map<String, String> mcpNames) {
        if (json == null || json.isBlank()) {
            return new TextRewrite(json, false);
        }
        validateStringArray(json, "MCP 工具配置");
        try {
            ArrayNode tools = (ArrayNode) objectMapper.readTree(json);
            boolean missing = false;
            for (int i = 0; i < tools.size(); i++) {
                ToolNameRewrite rewrite = rewriteToolName(tools.get(i).asText(), exportedMcpNames, mcpNames);
                tools.set(i, objectMapper.getNodeFactory().textNode(rewrite.name()));
                missing |= rewrite.missingReference();
            }
            return new TextRewrite(objectMapper.writeValueAsString(tools), missing);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("MCP 工具配置不是有效的 JSON", e);
        }
    }

    private TextRewrite rewriteGuardrailTools(String configJson, Set<String> exportedMcpNames,
                                              Map<String, String> mcpNames) {
        try {
            JsonNode root = objectMapper.readTree(configJson);
            JsonNode rules = root == null ? null : root.get("rules");
            boolean missing = false;
            if (rules != null && rules.isArray()) {
                for (JsonNode rule : rules) {
                    JsonNode toolsNode = rule.get("tools");
                    if (!(toolsNode instanceof ArrayNode tools)) {
                        continue;
                    }
                    for (int i = 0; i < tools.size(); i++) {
                        if (!tools.get(i).isTextual()) {
                            continue;
                        }
                        ToolNameRewrite rewrite = rewriteToolName(tools.get(i).asText(), exportedMcpNames, mcpNames);
                        tools.set(i, objectMapper.getNodeFactory().textNode(rewrite.name()));
                        missing |= rewrite.missingReference();
                    }
                }
            }
            return new TextRewrite(objectMapper.writeValueAsString(root), missing);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("护栏策略配置不是有效的 JSON", e);
        }
    }

    private ToolNameRewrite rewriteToolName(String toolName, Set<String> exportedMcpNames,
                                            Map<String, String> mcpNames) {
        String matchedSourceName = null;
        for (String sourceName : exportedMcpNames) {
            if (sourceName != null && toolName.startsWith(sourceName + "__")
                    && (matchedSourceName == null || sourceName.length() > matchedSourceName.length())) {
                matchedSourceName = sourceName;
            }
        }
        if (matchedSourceName == null) {
            return new ToolNameRewrite(toolName, false);
        }
        String finalName = mcpNames.get(matchedSourceName);
        if (finalName == null) {
            return new ToolNameRewrite(toolName, true);
        }
        return new ToolNameRewrite(finalName + toolName.substring(matchedSourceName.length()), false);
    }

    private String appendMessage(String current, String message) {
        return current == null ? message : current + "；" + message;
    }

    private String errorMessage(Exception e) {
        if (e instanceof DataIntegrityViolationException) {
            return "数据库约束冲突，无法导入该配置";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        if (message.contains("Source:") || message.contains("JsonParseException")) {
            return "配置内容格式校验失败";
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private void recordSuccess(ConfigImportResultDTO result, String type, Long sourceId, String originalName,
                               Long newId, String finalName, String message) {
        record(result, type, sourceId, originalName, finalName, newId, "IMPORTED", message);
    }

    private void recordSkipped(ConfigImportResultDTO result, String type, Long sourceId, String name, String message) {
        record(result, type, sourceId, name, null, null, "SKIPPED", message);
    }

    private void recordFailure(ConfigImportResultDTO result, String type, Long sourceId, String name, String message) {
        record(result, type, sourceId, name, null, null, "FAILED", message);
    }

    private void record(ConfigImportResultDTO result, String type, Long sourceId, String originalName,
                        String finalName, Long newId, String status, String message) {
        ConfigImportResultDTO.ItemResult item = new ConfigImportResultDTO.ItemResult();
        item.setType(type);
        item.setSourceId(sourceId);
        item.setOriginalName(originalName);
        item.setFinalName(finalName);
        item.setNewId(newId);
        item.setStatus(status);
        item.setMessage(message);
        result.getItems().add(item);

        ConfigImportResultDTO.CategoryResult category = result.getCategories().stream()
                .filter(candidate -> candidate.getType().equals(type))
                .findFirst()
                .orElseGet(() -> {
                    ConfigImportResultDTO.CategoryResult created = new ConfigImportResultDTO.CategoryResult(type);
                    result.getCategories().add(created);
                    return created;
                });
        result.setTotalCount(result.getTotalCount() + 1);
        category.setTotalCount(category.getTotalCount() + 1);
        if ("IMPORTED".equals(status)) {
            result.setImportedCount(result.getImportedCount() + 1);
            category.setImportedCount(category.getImportedCount() + 1);
            if (originalName != null && finalName != null && !originalName.equals(finalName)) {
                result.setRenamedCount(result.getRenamedCount() + 1);
                category.setRenamedCount(category.getRenamedCount() + 1);
            }
        } else if ("SKIPPED".equals(status)) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            category.setSkippedCount(category.getSkippedCount() + 1);
        } else {
            result.setFailedCount(result.getFailedCount() + 1);
            category.setFailedCount(category.getFailedCount() + 1);
        }
    }

    private AgentConfigBundleDTO.LlmData exportLlm(Llm entity) {
        AgentConfigBundleDTO.LlmData data = new AgentConfigBundleDTO.LlmData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setEndpointUrl(entity.getEndpointUrl());
        data.setSk(entity.getSk());
        data.setModel(entity.getModel());
        data.setMaxTokens(entity.getMaxTokens());
        data.setContextLength(entity.getContextLength());
        data.setTemperature(entity.getTemperature());
        data.setCapabilities(entity.getCapabilities());
        data.setReasoningEffortOptions(entity.getReasoningEffortOptions());
        return data;
    }

    private AgentConfigBundleDTO.EmbeddingModelData exportEmbeddingModel(EmbeddingModel entity) {
        AgentConfigBundleDTO.EmbeddingModelData data = new AgentConfigBundleDTO.EmbeddingModelData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setEndpointUrl(entity.getEndpointUrl());
        data.setSk(entity.getSk());
        data.setModel(entity.getModel());
        data.setDimensions(entity.getDimensions());
        return data;
    }

    private AgentConfigBundleDTO.McpServiceData exportMcpService(McpServiceConfig entity) {
        AgentConfigBundleDTO.McpServiceData data = new AgentConfigBundleDTO.McpServiceData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setDescription(entity.getDescription());
        data.setProtocol(entity.getProtocol());
        data.setConfig(entity.getConfig());
        data.setEnabled(entity.getEnabled());
        return data;
    }

    private AgentConfigBundleDTO.GuardrailPolicyData exportGuardrailPolicy(GuardrailPolicy entity) {
        AgentConfigBundleDTO.GuardrailPolicyData data = new AgentConfigBundleDTO.GuardrailPolicyData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setConfigJson(entity.getConfigJson());
        return data;
    }

    private AgentConfigBundleDTO.AgentData exportAgent(Agent entity) {
        AgentConfigBundleDTO.AgentData data = new AgentConfigBundleDTO.AgentData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setPersona(entity.getPersona());
        data.setEnabledTools(entity.getEnabledTools());
        data.setEnabledMcpTools(entity.getEnabledMcpTools());
        data.setLlmId(entity.getLlmId());
        data.setSecondaryLlmId(entity.getSecondaryLlmId());
        data.setEmbeddingModelId(entity.getEmbeddingModelId());
        data.setAggressiveMemoryRecall(entity.getAggressiveMemoryRecall());
        return data;
    }

    private AgentConfigBundleDTO.EnvironmentVariableData exportEnvironmentVariable(EnvironmentVariable entity) {
        AgentConfigBundleDTO.EnvironmentVariableData data = new AgentConfigBundleDTO.EnvironmentVariableData();
        data.setSourceId(entity.getId());
        data.setAgentId(entity.getAgentId());
        data.setKey(entity.getKeyName());
        data.setValue(entity.getKeyValue());
        return data;
    }

    private AgentConfigBundleDTO.ScheduledTaskData exportScheduledTask(ScheduledTask entity) {
        AgentConfigBundleDTO.ScheduledTaskData data = new AgentConfigBundleDTO.ScheduledTaskData();
        data.setSourceId(entity.getId());
        data.setName(entity.getName());
        data.setAgentId(entity.getAgentId());
        data.setUserPrompt(entity.getUserPrompt());
        data.setCronExpression(entity.getCronExpression());
        data.setCreateNewSession(entity.getCreateNewSession());
        data.setEnabled(entity.getEnabled());
        return data;
    }

    private record TextRewrite(String text, boolean missingReference) {
    }

    private record ToolNameRewrite(String name, boolean missingReference) {
    }
}
