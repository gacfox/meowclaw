package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "智能体相关系统配置导入导出包")
public class AgentConfigBundleDTO {
    private Integer formatVersion;
    private Long exportedAt;
    private List<LlmData> llms;
    private List<EmbeddingModelData> embeddingModels;
    private List<McpServiceData> mcpServices;
    private List<GuardrailPolicyData> guardrailPolicies;
    private List<AgentData> agents;
    private List<EnvironmentVariableData> environmentVariables;
    private List<ScheduledTaskData> scheduledTasks;

    @Getter
    @Setter
    public static class LlmData {
        private Long sourceId;
        private String name;
        private String endpointUrl;
        private String sk;
        private String model;
        private Integer maxTokens;
        private Integer contextLength;
        private Integer temperature;
        private String capabilities;
        private String reasoningEffortOptions;
    }

    @Getter
    @Setter
    public static class EmbeddingModelData {
        private Long sourceId;
        private String name;
        private String endpointUrl;
        private String sk;
        private String model;
        private Integer dimensions;
    }

    @Getter
    @Setter
    public static class McpServiceData {
        private Long sourceId;
        private String name;
        private String description;
        private String protocol;
        private String config;
        private Boolean enabled;
    }

    @Getter
    @Setter
    public static class GuardrailPolicyData {
        private Long sourceId;
        private String name;
        private String configJson;
    }

    @Getter
    @Setter
    public static class AgentData {
        private Long sourceId;
        private String name;
        private String persona;
        private String enabledTools;
        private String enabledMcpTools;
        private Long llmId;
        private Long secondaryLlmId;
        private Long embeddingModelId;
        private Boolean aggressiveMemoryRecall;
    }

    @Getter
    @Setter
    public static class EnvironmentVariableData {
        private Long sourceId;
        private Long agentId;
        private String key;
        private String value;
    }

    @Getter
    @Setter
    public static class ScheduledTaskData {
        private Long sourceId;
        private String name;
        private Long agentId;
        private String userPrompt;
        private String cronExpression;
        private Boolean createNewSession;
        private Boolean enabled;
    }
}
