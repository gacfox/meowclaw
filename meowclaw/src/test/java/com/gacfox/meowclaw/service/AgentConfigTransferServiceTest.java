package com.gacfox.meowclaw.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentConfigTransferServiceTest {
    private LlmRepository llmRepository;
    private EmbeddingModelRepository embeddingModelRepository;
    private McpServiceConfigRepository mcpServiceRepository;
    private GuardrailPolicyRepository guardrailPolicyRepository;
    private AgentRepository agentRepository;
    private EnvironmentVariableRepository environmentVariableRepository;
    private ScheduledTaskRepository scheduledTaskRepository;
    private AgentConfigImportItemService importItemService;
    private ObjectMapper objectMapper;
    private AgentConfigTransferService service;

    @BeforeEach
    void setUp() {
        llmRepository = mock(LlmRepository.class);
        embeddingModelRepository = mock(EmbeddingModelRepository.class);
        mcpServiceRepository = mock(McpServiceConfigRepository.class);
        guardrailPolicyRepository = mock(GuardrailPolicyRepository.class);
        agentRepository = mock(AgentRepository.class);
        environmentVariableRepository = mock(EnvironmentVariableRepository.class);
        scheduledTaskRepository = mock(ScheduledTaskRepository.class);
        importItemService = mock(AgentConfigImportItemService.class);
        objectMapper = new ObjectMapper();
        service = new AgentConfigTransferService(llmRepository, embeddingModelRepository,
                mcpServiceRepository, guardrailPolicyRepository, agentRepository,
                environmentVariableRepository, scheduledTaskRepository, importItemService, objectMapper);
    }

    @Test
    void exportIncludesSecretsButExcludesRuntimeAndEntityMetadata() throws Exception {
        Llm llm = new Llm();
        llm.setId(1L);
        llm.setName("primary");
        llm.setEndpointUrl("https://llm.example.com");
        llm.setSk("llm-secret");
        llm.setModel("model-a");
        llm.setCreatedAt(101L);
        llm.setUpdatedAt(102L);

        EmbeddingModel embeddingModel = new EmbeddingModel();
        embeddingModel.setId(2L);
        embeddingModel.setName("embedding");
        embeddingModel.setEndpointUrl("https://embedding.example.com");
        embeddingModel.setSk("embedding-secret");
        embeddingModel.setModel("embedding-a");
        embeddingModel.setDimensions(1024);
        embeddingModel.setCreatedAt(201L);
        embeddingModel.setUpdatedAt(202L);

        McpServiceConfig mcp = new McpServiceConfig();
        mcp.setId(3L);
        mcp.setName("git");
        mcp.setProtocol("stdio");
        mcp.setConfig("{\"command\":\"git-mcp\"}");
        mcp.setEnabled(true);
        mcp.setStatus("CONNECTED");
        mcp.setToolsCache("[\"status\"]");
        mcp.setErrorMessage("stale error");
        mcp.setLastCheckedAt(301L);
        mcp.setCreatedAt(302L);
        mcp.setUpdatedAt(303L);

        GuardrailPolicy guardrail = new GuardrailPolicy();
        guardrail.setId(4L);
        guardrail.setName("safety");
        guardrail.setConfigJson("{\"rules\":[]}");
        guardrail.setCreatedAt(401L);
        guardrail.setUpdatedAt(402L);

        Agent agent = new Agent();
        agent.setId(5L);
        agent.setName("assistant");
        agent.setLlmId(1L);
        agent.setSecondaryLlmId(1L);
        agent.setEmbeddingModelId(2L);
        agent.setAvatarUrl("https://example.com/avatar.png");
        agent.setWorkspaceFolder("C:/private/workspace");
        agent.setCreatedAt(501L);
        agent.setUpdatedAt(502L);

        EnvironmentVariable variable = new EnvironmentVariable();
        variable.setId(6L);
        variable.setAgentId(5L);
        variable.setKeyName("API_TOKEN");
        variable.setKeyValue("variable-secret");
        variable.setCreatedAt(601L);
        variable.setUpdatedAt(602L);

        ScheduledTask task = new ScheduledTask();
        task.setId(7L);
        task.setName("daily");
        task.setAgentId(5L);
        task.setUserPrompt("run");
        task.setCronExpression("0 0 9 * * ?");
        task.setEnabled(true);
        task.setLastStatus("SUCCESS");
        task.setLastExecutedAt(701L);
        task.setCreatedAt(702L);
        task.setUpdatedAt(703L);

        when(llmRepository.findAll()).thenReturn(List.of(llm));
        when(embeddingModelRepository.findAll()).thenReturn(List.of(embeddingModel));
        when(mcpServiceRepository.findAll()).thenReturn(List.of(mcp));
        when(guardrailPolicyRepository.findAll()).thenReturn(List.of(guardrail));
        when(agentRepository.findAll()).thenReturn(List.of(agent));
        when(environmentVariableRepository.findAll()).thenReturn(List.of(variable));
        when(scheduledTaskRepository.findAll()).thenReturn(List.of(task));

        JsonNode root = objectMapper.readTree(service.exportConfig());

        assertThat(root.get("formatVersion").asInt()).isEqualTo(AgentConfigTransferService.FORMAT_VERSION);
        Map<String, String> arrays = Map.of(
                "llms", "1",
                "embeddingModels", "2",
                "mcpServices", "3",
                "guardrailPolicies", "4",
                "agents", "5",
                "environmentVariables", "6",
                "scheduledTasks", "7");
        arrays.forEach((field, sourceId) -> {
            JsonNode item = root.get(field).get(0);
            assertThat(item.get("sourceId").asText()).as(field).isEqualTo(sourceId);
            assertThat(item.has("createdAt")).as(field + " createdAt").isFalse();
            assertThat(item.has("updatedAt")).as(field + " updatedAt").isFalse();
        });
        assertThat(root.at("/llms/0/sk").asText()).isEqualTo("llm-secret");
        assertThat(root.at("/embeddingModels/0/sk").asText()).isEqualTo("embedding-secret");
        assertThat(root.at("/environmentVariables/0/value").asText()).isEqualTo("variable-secret");
        assertThat(root.at("/mcpServices/0/config").asText()).contains("git-mcp");

        JsonNode exportedMcp = root.at("/mcpServices/0");
        assertThat(exportedMcp.has("status")).isFalse();
        assertThat(exportedMcp.has("toolsCache")).isFalse();
        assertThat(exportedMcp.has("errorMessage")).isFalse();
        assertThat(exportedMcp.has("lastCheckedAt")).isFalse();
        JsonNode exportedTask = root.at("/scheduledTasks/0");
        assertThat(exportedTask.has("lastStatus")).isFalse();
        assertThat(exportedTask.has("lastExecutedAt")).isFalse();
        JsonNode exportedAgent = root.at("/agents/0");
        assertThat(exportedAgent.has("workspaceFolder")).isFalse();
        assertThat(exportedAgent.has("avatarUrl")).isFalse();
    }

    @Test
    void importCompleteBundleRemapsReferencesAndCountsEveryCategory() throws Exception {
        AgentConfigBundleDTO bundle = emptyBundle();
        AgentConfigBundleDTO.LlmData primary = llm(1L, "primary");
        AgentConfigBundleDTO.LlmData secondary = llm(2L, "secondary");
        bundle.setLlms(List.of(primary, secondary));
        AgentConfigBundleDTO.EmbeddingModelData embedding = new AgentConfigBundleDTO.EmbeddingModelData();
        embedding.setSourceId(10L);
        embedding.setName("embedding");
        bundle.setEmbeddingModels(List.of(embedding));
        AgentConfigBundleDTO.McpServiceData mcp = mcp(20L, "filesystem", true);
        bundle.setMcpServices(List.of(mcp));
        AgentConfigBundleDTO.GuardrailPolicyData guardrail = new AgentConfigBundleDTO.GuardrailPolicyData();
        guardrail.setSourceId(30L);
        guardrail.setName("safety");
        guardrail.setConfigJson("{\"rules\":[{\"tools\":[\"filesystem__read\"]}]}");
        bundle.setGuardrailPolicies(List.of(guardrail));
        AgentConfigBundleDTO.AgentData agent = agent(40L, "assistant", 1L, 2L);
        agent.setEmbeddingModelId(10L);
        agent.setEnabledTools("[\"read_file\"]");
        agent.setEnabledMcpTools("[\"filesystem__read\"]");
        bundle.setAgents(List.of(agent));
        AgentConfigBundleDTO.EnvironmentVariableData variable = environmentVariable(50L, 40L, "API_TOKEN", "secret");
        bundle.setEnvironmentVariables(List.of(variable));
        AgentConfigBundleDTO.ScheduledTaskData task = scheduledTask(60L, 40L, "daily", true);
        bundle.setScheduledTasks(List.of(task));

        when(importItemService.createLlm(any())).thenAnswer(invocation -> {
            AgentConfigBundleDTO.LlmData data = invocation.getArgument(0);
            return llmDto(data.getSourceId() + 100, data.getName());
        });
        when(importItemService.createEmbeddingModel(any())).thenReturn(embeddingDto(201L, "embedding"));
        when(importItemService.createMcpService(any(), anyString())).thenAnswer(invocation ->
                mcpDto(301L, invocation.getArgument(1)));
        when(importItemService.createGuardrailPolicy(any(), anyString(), anyString())).thenAnswer(invocation ->
                guardrailDto(401L, invocation.getArgument(1)));
        when(importItemService.createAgent(any(), any(), any(), any(), any(), any())).thenReturn(agentDto(501L, "assistant"));
        when(importItemService.createEnvironmentVariable(any(), anyString(), anyString())).thenAnswer(invocation ->
                environmentVariableDto(601L, invocation.getArgument(0), invocation.getArgument(1)));
        when(importItemService.createScheduledTask(any(), any())).thenReturn(scheduledTaskDto(701L, "daily"));

        ConfigImportResultDTO result = service.importConfig(file(bundle));

        assertThat(result.getTotalCount()).isEqualTo(8);
        assertThat(result.getImportedCount()).isEqualTo(8);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getRenamedCount()).isZero();
        assertThat(result.getCategories()).hasSize(7);
        assertThat(result.getCategories()).allSatisfy(category -> {
            int expectedCount = category.getType().equals("LLM") ? 2 : 1;
            assertThat(category.getTotalCount()).isEqualTo(expectedCount);
            assertThat(category.getImportedCount()).isEqualTo(expectedCount);
            assertThat(category.getFailedCount()).isZero();
            assertThat(category.getSkippedCount()).isZero();
        });
        verify(importItemService).createAgent(argThat(data -> data.getSourceId().equals(40L)),
                eq(101L), eq(102L), eq(201L), eq("[\"read_file\"]"), eq("[\"filesystem__read\"]"));
        verify(importItemService).createEnvironmentVariable(eq(501L), eq("API_TOKEN"), eq("secret"));
        verify(importItemService).createScheduledTask(argThat(data -> data.getSourceId().equals(60L)), eq(501L));
        assertThat(item(result, "AGENT", 40L).getNewId()).isEqualTo(501L);
        assertThat(item(result, "ENVIRONMENT_VARIABLE", 50L).getNewId()).isEqualTo(601L);
        assertThat(item(result, "SCHEDULED_TASK", 60L).getNewId()).isEqualTo(701L);
        assertThat(item(result, "MCP_SERVICE", 20L).getMessage()).isEqualTo("已按安全策略保持禁用");
        assertThat(item(result, "SCHEDULED_TASK", 60L).getMessage()).isEqualTo("已按安全策略保持禁用");
    }

    @Test
    void importRenamesConflictsRewritesToolPrefixesAndSkipsExistingVariable() throws Exception {
        AgentConfigBundleDTO bundle = emptyBundle();
        bundle.setLlms(List.of(llm(1L, "primary")));
        bundle.setMcpServices(List.of(mcp(2L, "git", false)));
        AgentConfigBundleDTO.GuardrailPolicyData guardrail = new AgentConfigBundleDTO.GuardrailPolicyData();
        guardrail.setSourceId(3L);
        guardrail.setName("safety");
        guardrail.setConfigJson("{\"rules\":[{\"tools\":[\"git__*\",\"git__status\",\"other__*\"]}]}");
        bundle.setGuardrailPolicies(List.of(guardrail));
        AgentConfigBundleDTO.AgentData agent = agent(4L, "assistant", 1L, 1L);
        agent.setEnabledTools("[\"read_file\"]");
        agent.setEnabledMcpTools("[\"git__status\",\"git__*\",\"other__tool\"]");
        bundle.setAgents(List.of(agent));
        bundle.setEnvironmentVariables(List.of(environmentVariable(5L, 4L, "TOKEN", "new-value")));

        when(mcpServiceRepository.findByName("git")).thenReturn(Optional.of(new McpServiceConfig()));
        when(guardrailPolicyRepository.existsByName("safety")).thenReturn(true);
        when(importItemService.createLlm(any())).thenReturn(llmDto(101L, "primary"));
        when(importItemService.createMcpService(any(), anyString())).thenAnswer(invocation ->
                mcpDto(201L, invocation.getArgument(1)));
        when(importItemService.createGuardrailPolicy(any(), anyString(), anyString())).thenAnswer(invocation ->
                guardrailDto(301L, invocation.getArgument(1)));
        when(importItemService.createAgent(any(), any(), any(), any(), any(), any())).thenReturn(agentDto(401L, "assistant"));
        when(environmentVariableRepository.findByAgentIdAndKeyNameIgnoreCase(401L, "TOKEN"))
                .thenReturn(Optional.of(new EnvironmentVariable()));

        ConfigImportResultDTO result = service.importConfig(file(bundle));

        ArgumentCaptor<String> mcpName = ArgumentCaptor.forClass(String.class);
        verify(importItemService).createMcpService(any(), mcpName.capture());
        assertThat(mcpName.getValue()).isEqualTo("git (导入)");
        ArgumentCaptor<String> guardrailName = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> guardrailConfig = ArgumentCaptor.forClass(String.class);
        verify(importItemService).createGuardrailPolicy(any(), guardrailName.capture(), guardrailConfig.capture());
        assertThat(guardrailName.getValue()).isEqualTo("safety (导入)");
        JsonNode guardrailTools = objectMapper.readTree(guardrailConfig.getValue()).at("/rules/0/tools");
        assertThat(guardrailTools.get(0).asText()).isEqualTo("git (导入)__*");
        assertThat(guardrailTools.get(1).asText()).isEqualTo("git (导入)__status");
        assertThat(guardrailTools.get(2).asText()).isEqualTo("other__*");

        ArgumentCaptor<String> agentMcpTools = ArgumentCaptor.forClass(String.class);
        verify(importItemService).createAgent(any(), eq(101L), eq(101L), isNull(),
                eq("[\"read_file\"]"), agentMcpTools.capture());
        JsonNode rewrittenAgentTools = objectMapper.readTree(agentMcpTools.getValue());
        assertThat(rewrittenAgentTools.get(0).asText()).isEqualTo("git (导入)__status");
        assertThat(rewrittenAgentTools.get(1).asText()).isEqualTo("git (导入)__*");
        assertThat(rewrittenAgentTools.get(2).asText()).isEqualTo("other__tool");

        verify(importItemService, never()).createEnvironmentVariable(any(), any(), any());
        assertThat(item(result, "ENVIRONMENT_VARIABLE", 5L).getStatus()).isEqualTo("SKIPPED");
        assertThat(item(result, "ENVIRONMENT_VARIABLE", 5L).getMessage()).contains("未覆盖原值");
        assertThat(result.getTotalCount()).isEqualTo(5);
        assertThat(result.getImportedCount()).isEqualTo(4);
        assertThat(result.getSkippedCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        assertThat(result.getRenamedCount()).isEqualTo(2);
        assertThat(item(result, "MCP_SERVICE", 2L).getFinalName()).isEqualTo("git (导入)");
        assertThat(item(result, "GUARDRAIL_POLICY", 3L).getFinalName()).isEqualTo("safety (导入)");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void importSkipsAgentAndDependentsWhenEitherRequiredLlmFails(boolean primaryFails) throws Exception {
        AgentConfigBundleDTO bundle = emptyBundle();
        bundle.setLlms(List.of(llm(1L, "primary"), llm(2L, "secondary")));
        bundle.setAgents(List.of(agent(10L, "assistant", 1L, 2L)));
        bundle.setEnvironmentVariables(List.of(
                environmentVariable(20L, 10L, "AGENT_ONLY", "discard"),
                environmentVariable(21L, null, "GLOBAL", "keep")));
        bundle.setScheduledTasks(List.of(scheduledTask(30L, 10L, "dependent", false)));
        when(importItemService.createLlm(any())).thenAnswer(invocation -> {
            AgentConfigBundleDTO.LlmData data = invocation.getArgument(0);
            if (data.getSourceId().equals(primaryFails ? 1L : 2L)) {
                throw new IllegalStateException("模型创建失败");
            }
            return llmDto(data.getSourceId() + 100, data.getName());
        });
        when(importItemService.createEnvironmentVariable(isNull(), eq("GLOBAL"), eq("keep")))
                .thenReturn(environmentVariableDto(601L, null, "GLOBAL"));

        ConfigImportResultDTO result = service.importConfig(file(bundle));

        verify(importItemService, never()).createAgent(any(), any(), any(), any(), any(), any());
        verify(importItemService, times(1)).createEnvironmentVariable(any(), anyString(), anyString());
        verify(importItemService).createEnvironmentVariable(isNull(), eq("GLOBAL"), eq("keep"));
        verify(importItemService, never()).createScheduledTask(any(), any());
        assertThat(item(result, "AGENT", 10L).getStatus()).isEqualTo("SKIPPED");
        assertThat(item(result, "ENVIRONMENT_VARIABLE", 20L).getStatus()).isEqualTo("SKIPPED");
        assertThat(item(result, "SCHEDULED_TASK", 30L).getStatus()).isEqualTo("SKIPPED");
        assertThat(item(result, "ENVIRONMENT_VARIABLE", 21L).getStatus()).isEqualTo("IMPORTED");
        assertThat(result.getTotalCount()).isEqualTo(6);
        assertThat(result.getImportedCount()).isEqualTo(2);
        assertThat(result.getSkippedCount()).isEqualTo(3);
        assertThat(result.getFailedCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("invalidBundles")
    void importRejectsInvalidBundleBeforeWritingAnyItem(String json, String expectedMessage) {
        assertThatThrownBy(() -> service.importConfig(file(json)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);

        verifyNoInteractions(importItemService);
    }

    static Stream<Arguments> invalidBundles() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AgentConfigBundleDTO wrongVersion = emptyBundle();
        wrongVersion.setFormatVersion(AgentConfigTransferService.FORMAT_VERSION + 1);
        wrongVersion.setLlms(List.of(llm(1L, "must-not-write")));

        AgentConfigBundleDTO missingArray = emptyBundle();
        missingArray.setLlms(List.of(llm(1L, "must-not-write")));
        missingArray.setScheduledTasks(null);

        AgentConfigBundleDTO duplicateSourceId = emptyBundle();
        duplicateSourceId.setLlms(List.of(llm(1L, "first"), llm(1L, "second")));

        return Stream.of(
                Arguments.of("{not-json", "配置文件格式无效"),
                Arguments.of(mapper.writeValueAsString(wrongVersion), "不支持的配置文件版本"),
                Arguments.of(mapper.writeValueAsString(missingArray), "缺少 scheduledTasks 数组"),
                Arguments.of(mapper.writeValueAsString(duplicateSourceId), "重复的 sourceId"));
    }

    private static AgentConfigBundleDTO emptyBundle() {
        AgentConfigBundleDTO bundle = new AgentConfigBundleDTO();
        bundle.setFormatVersion(AgentConfigTransferService.FORMAT_VERSION);
        bundle.setExportedAt(1L);
        bundle.setLlms(List.of());
        bundle.setEmbeddingModels(List.of());
        bundle.setMcpServices(List.of());
        bundle.setGuardrailPolicies(List.of());
        bundle.setAgents(List.of());
        bundle.setEnvironmentVariables(List.of());
        bundle.setScheduledTasks(List.of());
        return bundle;
    }

    private static AgentConfigBundleDTO.LlmData llm(Long sourceId, String name) {
        AgentConfigBundleDTO.LlmData data = new AgentConfigBundleDTO.LlmData();
        data.setSourceId(sourceId);
        data.setName(name);
        data.setEndpointUrl("https://llm.example.com");
        data.setSk("secret");
        data.setModel("model");
        return data;
    }

    private static AgentConfigBundleDTO.McpServiceData mcp(Long sourceId, String name, boolean enabled) {
        AgentConfigBundleDTO.McpServiceData data = new AgentConfigBundleDTO.McpServiceData();
        data.setSourceId(sourceId);
        data.setName(name);
        data.setProtocol("stdio");
        data.setConfig("{}");
        data.setEnabled(enabled);
        return data;
    }

    private static AgentConfigBundleDTO.AgentData agent(Long sourceId, String name, Long llmId, Long secondaryLlmId) {
        AgentConfigBundleDTO.AgentData data = new AgentConfigBundleDTO.AgentData();
        data.setSourceId(sourceId);
        data.setName(name);
        data.setLlmId(llmId);
        data.setSecondaryLlmId(secondaryLlmId);
        return data;
    }

    private static AgentConfigBundleDTO.EnvironmentVariableData environmentVariable(Long sourceId, Long agentId,
                                                                                     String key, String value) {
        AgentConfigBundleDTO.EnvironmentVariableData data = new AgentConfigBundleDTO.EnvironmentVariableData();
        data.setSourceId(sourceId);
        data.setAgentId(agentId);
        data.setKey(key);
        data.setValue(value);
        return data;
    }

    private static AgentConfigBundleDTO.ScheduledTaskData scheduledTask(Long sourceId, Long agentId,
                                                                        String name, boolean enabled) {
        AgentConfigBundleDTO.ScheduledTaskData data = new AgentConfigBundleDTO.ScheduledTaskData();
        data.setSourceId(sourceId);
        data.setAgentId(agentId);
        data.setName(name);
        data.setUserPrompt("run");
        data.setCronExpression("0 0 9 * * ?");
        data.setCreateNewSession(true);
        data.setEnabled(enabled);
        return data;
    }

    private static LlmDTO llmDto(Long id, String name) {
        LlmDTO dto = new LlmDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static EmbeddingModelDTO embeddingDto(Long id, String name) {
        EmbeddingModelDTO dto = new EmbeddingModelDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static McpServiceDTO mcpDto(Long id, String name) {
        McpServiceDTO dto = new McpServiceDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static GuardrailPolicyDTO guardrailDto(Long id, String name) {
        GuardrailPolicyDTO dto = new GuardrailPolicyDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static AgentDTO agentDto(Long id, String name) {
        AgentDTO dto = new AgentDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private static EnvironmentVariableDTO environmentVariableDto(Long id, Long agentId, String key) {
        EnvironmentVariableDTO dto = new EnvironmentVariableDTO();
        dto.setId(id);
        dto.setAgentId(agentId);
        dto.setKey(key);
        return dto;
    }

    private static ScheduledTaskDTO scheduledTaskDto(Long id, String name) {
        ScheduledTaskDTO dto = new ScheduledTaskDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    private MockMultipartFile file(AgentConfigBundleDTO bundle) throws Exception {
        return file(objectMapper.writeValueAsString(bundle));
    }

    private static MockMultipartFile file(String json) {
        return new MockMultipartFile("file", "config.json", "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    private static ConfigImportResultDTO.ItemResult item(ConfigImportResultDTO result, String type, Long sourceId) {
        return result.getItems().stream()
                .filter(item -> item.getType().equals(type) && item.getSourceId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing result item " + type + "/" + sourceId));
    }
}
