package com.gacfox.meowclaw.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MemoryWriteParam {
    @JsonProperty("type")
    @AgenticToolParam(name = "type", description = "记忆类型，可选项为：fact / preference / rule")
    private String type;

    @JsonProperty("content")
    @AgenticToolParam(name = "content", description = "记忆内容")
    private String content;
}
