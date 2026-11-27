package com.gacfox.meowclaw.service.agent;

import com.gacfox.meowclaw.dto.agent.AgentConfigBundleDTO;
import com.gacfox.meowclaw.dto.agent.AgentDTO;
import com.gacfox.meowclaw.dto.agent.CreateAgentRequest;
import com.gacfox.meowclaw.dto.environment.CreateEnvironmentVariableRequest;
import com.gacfox.meowclaw.dto.environment.EnvironmentVariableDTO;
import com.gacfox.meowclaw.dto.guardrail.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.guardrail.SaveGuardrailPolicyRequest;
import com.gacfox.meowclaw.dto.mcp.CreateMcpServiceRequest;
import com.gacfox.meowclaw.dto.mcp.McpServiceDTO;
import com.gacfox.meowclaw.dto.model.CreateEmbeddingModelRequest;
import com.gacfox.meowclaw.dto.model.CreateLlmRequest;
import com.gacfox.meowclaw.dto.model.EmbeddingModelDTO;
import com.gacfox.meowclaw.dto.model.LlmDTO;
import com.gacfox.meowclaw.dto.schedule.CreateScheduledTaskRequest;
import com.gacfox.meowclaw.dto.schedule.ScheduledTaskDTO;
import com.gacfox.meowclaw.service.environment.EnvironmentVariableService;
import com.gacfox.meowclaw.service.guardrail.GuardrailPolicyService;
import com.gacfox.meowclaw.service.mcp.McpService;
import com.gacfox.meowclaw.service.model.EmbeddingModelService;
import com.gacfox.meowclaw.service.model.LlmService;
import com.gacfox.meowclaw.service.schedule.ScheduledTaskService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
        request.setAllowAsSubagent(data.getAllowAsSubagent());
        request.setSubagentDescription(data.getSubagentDescription());
        validate(request);
        return agentService.create(request);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentDTO updateProfessionalSubagents(Long agentId, List<Long> professionalSubagentIds) {
        return agentService.updateProfessionalSubagents(agentId, professionalSubagentIds);
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
