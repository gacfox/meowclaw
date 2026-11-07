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
@Schema(description = "安装技能包请求")
public class SkillInstallRequest {
    @NotNull(message = "agentId 不能为空")
    @Schema(description = "智能体 ID")
    private Long agentId;

    @Schema(description = "是否覆盖已存在的文件")
    private Boolean overwrite;
}
