package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "更新项目请求")
public class UpdateProjectRequest {
    @Schema(description = "项目名称，null表示不修改")
    private String name;

    @Schema(description = "项目路径，null表示不修改")
    private String path;
}
