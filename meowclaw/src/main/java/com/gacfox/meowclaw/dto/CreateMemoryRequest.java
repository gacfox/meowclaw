package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建记忆请求")
public class CreateMemoryRequest {
    @NotNull(message = "智能体ID不能为空")
    @Schema(description = "智能体 ID")
    private Long agentId;
    @NotBlank(message = "记忆类型不能为空")
    @Schema(description = "记忆类型")
    private String type;
    @NotBlank(message = "记忆内容不能为空")
    @Schema(description = "记忆内容")
    private String content;
}
