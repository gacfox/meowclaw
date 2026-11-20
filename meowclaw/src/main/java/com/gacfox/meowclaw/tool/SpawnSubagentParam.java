package com.gacfox.meowclaw.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SpawnSubagentParam {
    @JsonProperty("prompt")
    @AgenticToolParam(name = "prompt", description = "子智能体的完整任务指令")
    private String prompt;

    @JsonProperty("description")
    @AgenticToolParam(name = "description", description = "子任务的简短描述，用于日志和状态展示", required = false)
    private String description;

    @JsonProperty("agent_id")
    @AgenticToolParam(name = "agent_id", description = "专业子智能体ID；缺省时创建general子智能体", required = false)
    private Long agentId;

    @JsonProperty("allowed_tools")
    @AgenticToolParam(name = "allowed_tools", description = "仅general子智能体可用：允许使用的工具名列表，必须是父智能体工具列表的子集", required = false)
    private List<String> allowedTools;
}
