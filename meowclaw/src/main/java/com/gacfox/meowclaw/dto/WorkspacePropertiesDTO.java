package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "工作区路径递归属性")
public class WorkspacePropertiesDTO {
    private final long files;
    private final long directories;
    private final long totalSize;
    private final long lastModified;
}
