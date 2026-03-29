package com.gacfox.meowclaw.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SkillUpdateDto {
    @NotBlank(message = "技能名称不能为空")
    private String name;
    private String description;
}
