package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
@Schema(description = "工作区分块上传会话状态")
public class UploadSessionDTO {
    private final String uploadId;
    private final String fileName;
    private final long fileSize;
    private final int chunkSize;
    private final int totalChunks;
    private final List<Integer> receivedChunks;
}
