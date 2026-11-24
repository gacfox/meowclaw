package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "工作区重命名请求")
public class RenameEntryRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotNull(message = "路径不能为空")
    private String path;

    @NotNull(message = "新名称不能为空")
    private String newName;

    @NotNull(message = "冲突策略不能为空")
    private String conflict;
}
