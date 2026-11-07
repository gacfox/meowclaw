package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillInstallRequest {
    @NotNull(message = "agentId 不能为空")
    private Long agentId;

    private Boolean overwrite;
}
