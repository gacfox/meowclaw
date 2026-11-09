package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "智能体环境变量配置")
public class AgentEnvironmentDTO {
    @Schema(description = "智能体 ID")
    private Long agentId;

    @Schema(description = "环境变量键值对")
    private Map<String, String> variables;
}
