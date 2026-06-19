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
public class ReadFileParam {
    @JsonProperty("path")
    @AgenticToolParam(name = "path", description = "要读取的文件路径")
    private String path;

    @JsonProperty("limit")
    @AgenticToolParam(name = "limit", description = "读取的行数，默认2000", required = false)
    private Integer limit;

    @JsonProperty("offset")
    @AgenticToolParam(name = "offset", description = "起始行偏移量(从0开始)，默认0", required = false)
    private Integer offset;
}
