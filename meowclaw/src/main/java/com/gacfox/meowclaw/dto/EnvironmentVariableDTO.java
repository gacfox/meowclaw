package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "环境变量")
public class EnvironmentVariableDTO {
    @Schema(description = "环境变量 ID")
    private Long id;
    @Schema(description = "智能体 ID，null表示全局")
    private Long agentId;
    @Schema(description = "变量名")
    private String key;
    @Schema(description = "变量值")
    private String value;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
