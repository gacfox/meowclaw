package com.gacfox.meowclaw.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@Schema(description = "工作区分块上传完成结果")
public class UploadCompleteDTO {
    private final FileEntryDTO entry;
    private final boolean skipped;
    private final String targetPath;
}
