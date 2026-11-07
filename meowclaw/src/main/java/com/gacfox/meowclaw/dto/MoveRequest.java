package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "移动工作区文件或目录请求")
public class MoveRequest {
    @NotNull(message = "智能体ID不能为空")
    @Schema(description = "智能体 ID")
    private Long agentId;

    @NotNull(message = "源路径不能为空")
    @Schema(description = "源路径")
    private String fromPath;

    @NotNull(message = "目标路径不能为空")
    @Schema(description = "目标路径")
    private String toPath;
}
