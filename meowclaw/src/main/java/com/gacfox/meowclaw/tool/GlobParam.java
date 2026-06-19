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
public class GlobParam {
    @JsonProperty("path")
    @AgenticToolParam(name = "path", description = "搜索的根目录路径")
    private String path;

    @JsonProperty("pattern")
    @AgenticToolParam(name = "pattern", description = "文件名匹配模式，支持*和**通配符")
    private String pattern;
}
