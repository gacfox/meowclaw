package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新智能体环境变量请求")
public class UpdateAgentEnvironmentRequest {
    @NotNull(message = "环境变量配置不能为空")
    @Schema(description = "环境变量键值对")
    private Map<String, String> variables;
}
