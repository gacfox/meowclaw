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
public class GrepParam {
    @JsonProperty("pattern")
    @AgenticToolParam(name = "pattern", description = "搜索的正则表达式模式")
    private String pattern;

    @JsonProperty("path")
    @AgenticToolParam(name = "path", description = "搜索的文件或目录路径")
    private String path;

    @JsonProperty("ignore_case")
    @AgenticToolParam(name = "ignore_case", description = "是否忽略大小写，默认false", required = false)
    private Boolean ignoreCase;

    @JsonProperty("show_line_numbers")
    @AgenticToolParam(name = "show_line_numbers", description = "是否显示行号，默认true", required = false)
    private Boolean showLineNumbers;
}
