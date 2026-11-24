package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "工作区多路径请求")
public class WorkspacePathsRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotEmpty(message = "路径不能为空")
    private List<String> paths;
}
