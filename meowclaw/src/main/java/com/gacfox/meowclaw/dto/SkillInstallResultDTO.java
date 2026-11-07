package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "技能包安装结果")
public class SkillInstallResultDTO {
    @Schema(description = "技能包安装状态")
    public enum Status {INSTALLED, CONFLICT}

    @Schema(description = "安装状态")
    private final Status status;
    @Schema(description = "已存在的冲突文件列表")
    private final List<String> existingFiles;
}
