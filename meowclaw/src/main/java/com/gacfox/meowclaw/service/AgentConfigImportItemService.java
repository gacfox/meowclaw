package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.AgentConfigBundleDTO;
import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.CreateEmbeddingModelRequest;
import com.gacfox.meowclaw.dto.CreateEnvironmentVariableRequest;
import com.gacfox.meowclaw.dto.CreateLlmRequest;
import com.gacfox.meowclaw.dto.CreateMcpServiceRequest;
import com.gacfox.meowclaw.dto.CreateScheduledTaskRequest;
import com.gacfox.meowclaw.dto.EmbeddingModelDTO;
import com.gacfox.meowclaw.dto.EnvironmentVariableDTO;
import com.gacfox.meowclaw.dto.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.McpServiceDTO;
import com.gacfox.meowclaw.dto.SaveGuardrailPolicyRequest;
import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentConfigImportItemService {
    private final LlmService llmService;
    private final EmbeddingModelService embeddingModelService;
    private final McpService mcpService;
    private final GuardrailPolicyService guardrailPolicyService;
    private final AgentService agentService;
    private final EnvironmentVariableService environmentVariableService;
    private final ScheduledTaskService scheduledTaskService;
    private final Validator validator;

    @Autowired
    public AgentConfigImportItemService(LlmService llmService, EmbeddingModelService embeddingModelService,
                                        McpService mcpService, GuardrailPolicyService guardrailPolicyService,
                                        AgentService agentService, EnvironmentVariableService environmentVariableService,
                                        ScheduledTaskService scheduledTaskService, Validator validator) {
        this.llmService = llmService;
        this.embeddingModelService = embeddingModelService;
        this.mcpService = mcpService;
        this.guardrailPolicyService = guardrailPolicyService;
        this.agentService = agentService;
        this.environmentVariableService = environmentVariableService;
        this.scheduledTaskService = scheduledTaskService;
        this.validator = validator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LlmDTO createLlm(AgentConfigBundleDTO.LlmData data) {
        CreateLlmRequest request = new CreateLlmRequest(data.getName(), data.getEndpointUrl(), data.getSk(),
                data.getModel(), data.getMaxTokens(), data.getContextLength(), data.getTemperature(),
                data.getCapabilities(), data.getReasoningEffortOptions());
        validate(request);
        return llmService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmbeddingModelDTO createEmbeddingModel(AgentConfigBundleDTO.EmbeddingModelData data) {
        CreateEmbeddingModelRequest request = new CreateEmbeddingModelRequest(data.getName(), data.getEndpointUrl(),
                data.getSk(), data.getModel(), data.getDimensions());
        validate(request);
        return embeddingModelService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public McpServiceDTO createMcpService(AgentConfigBundleDTO.McpServiceData data, String finalName) {
        CreateMcpServiceRequest request = new CreateMcpServiceRequest(finalName, data.getDescription(),
                data.getProtocol(), data.getConfig());
        validate(request);
        return mcpService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GuardrailPolicyDTO createGuardrailPolicy(AgentConfigBundleDTO.GuardrailPolicyData data,
                                                     String finalName, String configJson) {
        if (finalName != null && finalName.length() > 100) {
            throw new IllegalArgumentException("策略名称长度不能超过100");
        }
        SaveGuardrailPolicyRequest request = new SaveGuardrailPolicyRequest();
        request.setName(finalName);
        request.setConfigJson(configJson);
        validate(request);
        return guardrailPolicyService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentDTO createAgent(AgentConfigBundleDTO.AgentData data, Long llmId, Long secondaryLlmId,
                                Long embeddingModelId, String enabledTools, String enabledMcpTools) {
        CreateAgentRequest request = new CreateAgentRequest(data.getName(), null, data.getPersona(), enabledTools,
                enabledMcpTools, llmId, secondaryLlmId, embeddingModelId, null, data.getAggressiveMemoryRecall());
        validate(request);
        return agentService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EnvironmentVariableDTO createEnvironmentVariable(Long agentId, String key, String value) {
        if (key != null && key.trim().length() > 255) {
            throw new IllegalArgumentException("环境变量名长度不能超过255");
        }
        CreateEnvironmentVariableRequest request = new CreateEnvironmentVariableRequest();
        request.setAgentId(agentId);
        request.setKey(key);
        request.setValue(value);
        validate(request);
        return environmentVariableService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ScheduledTaskDTO createScheduledTask(AgentConfigBundleDTO.ScheduledTaskData data, Long agentId) {
        CreateScheduledTaskRequest request = new CreateScheduledTaskRequest(data.getName(), agentId,
                data.getUserPrompt(), data.getCronExpression(), data.getCreateNewSession());
        validate(request);
        return scheduledTaskService.createImported(request);
    }

    private void validate(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("；")));
        }
    }
}
