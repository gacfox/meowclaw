package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "创建环境变量请求")
public class CreateEnvironmentVariableRequest {
    @Schema(description = "智能体 ID，null表示全局")
    private Long agentId;

    @NotBlank(message = "变量名不能为空")
    @Schema(description = "变量名")
    private String key;

    @NotNull(message = "变量值不能为null")
    @Schema(description = "变量值")
    private String value;
}
