package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "工作区批量复制或移动请求")
public class BatchTransferRequest {
    @NotNull(message = "智能体ID不能为空")
    private Long agentId;

    @NotEmpty(message = "源路径不能为空")
    private List<String> sourcePaths;

    @NotNull(message = "目标目录不能为空")
    private String targetDir;

    @NotNull(message = "冲突策略不能为空")
    private String conflict;
}
