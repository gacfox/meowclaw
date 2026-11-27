package com.gacfox.meowclaw.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "工作区批量操作单项结果")
public class BatchOperationItemDTO {
    private final String sourcePath;
    private final String targetPath;
    private final boolean success;
    private final boolean skipped;
    private final String message;
}
