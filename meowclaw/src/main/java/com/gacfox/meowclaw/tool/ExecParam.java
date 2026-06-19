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
public class ExecParam {
    @JsonProperty("command")
    @AgenticToolParam(name = "command", description = "要执行的shell命令")
    private String command;

    @JsonProperty("timeout")
    @AgenticToolParam(name = "timeout", description = "超时秒数，默认30", required = false)
    private Integer timeout;
}
