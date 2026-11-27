package com.gacfox.meowclaw.dto.workspace;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "完成工作区分块上传请求")
public class CompleteUploadRequest {
    @NotNull(message = "冲突策略不能为空")
    private String conflict;
}
