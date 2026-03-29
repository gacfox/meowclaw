package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class SkillDto {
    private Long id;
    private String name;
    private String description;
    private Long createdAt;
}
