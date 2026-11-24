package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "创建工作区分块上传会话请求")
public class CreateUploadSessionRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotNull(message = "目标目录不能为空")
    private String targetDir;

    @NotNull(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @NotNull(message = "块大小不能为空")
    private Integer chunkSize;

    @NotNull(message = "总块数不能为空")
    private Integer totalChunks;
}
