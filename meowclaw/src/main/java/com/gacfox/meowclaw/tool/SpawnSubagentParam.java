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
    @AgenticToolParam(name = "description", description = "子任务的简短描述，用于日志和状态展示")
    private String description;

    @JsonProperty("allowed_tools")
    @AgenticToolParam(name = "allowed_tools", description = "允许子智能体使用的工具名列表，必须是父智能体工具列表的子集")
    private List<String> allowedTools;
}
