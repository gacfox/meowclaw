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
public class MemoryRecallParam {
    @JsonProperty("query")
    @AgenticToolParam(name = "query", description = "查询文本")
    private String query;

    @JsonProperty("limit")
    @AgenticToolParam(name = "limit", description = "返回数量，1-20，默认3", required = false)
    private Integer limit;
}
