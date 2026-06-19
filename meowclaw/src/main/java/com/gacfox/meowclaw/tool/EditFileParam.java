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
public class EditFileParam {
    @JsonProperty("path")
    @AgenticToolParam(name = "path", description = "要编辑的文件路径")
    private String path;

    @JsonProperty("old_text")
    @AgenticToolParam(name = "old_text", description = "要替换的原始文本")
    private String oldText;

    @JsonProperty("new_text")
    @AgenticToolParam(name = "new_text", description = "替换后的新文本")
    private String newText;

    @JsonProperty("replace_all")
    @AgenticToolParam(name = "replace_all", description = "是否替换所有匹配项，默认false", required = false)
    private Boolean replaceAll;
}
