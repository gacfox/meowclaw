package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SkillInstallResultDTO {
    public enum Status { INSTALLED, CONFLICT }

    private final Status status;
    private final List<String> existingFiles;
}
