package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "创建项目请求")
public class CreateProjectRequest {
    @NotNull(message = "请选择智能体")
    @Schema(description = "智能体 ID")
    private Long agentId;

    @Schema(description = "项目名称")
    private String name;

    @Schema(description = "项目路径，空则使用工作区projects目录下的项目ID目录")
    private String path;
}
