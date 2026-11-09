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
public class WriteFileParam {
    @JsonProperty("path")
    @AgenticToolParam(name = "path", description = "要写入的文件路径（相对CWD或绝对路径）；CWD已是目标目录，不要重复拼接工作区根目录名称")
    private String path;

    @JsonProperty("content")
    @AgenticToolParam(name = "content", description = "要写入的文本内容，UTF-8")
    private String content;

    @JsonProperty("append")
    @AgenticToolParam(name = "append", description = "是否追加写入，默认false；true时文件不存在则创建", required = false)
    private Boolean append;
}
