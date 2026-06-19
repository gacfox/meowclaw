package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SkillPackageDTO {
    private Long id;
    private String name;
    private String description;
    private String storedFilename;
    private String originalFilename;
    private Long fileSize;
    private Long createdAt;
    private Long updatedAt;
}
