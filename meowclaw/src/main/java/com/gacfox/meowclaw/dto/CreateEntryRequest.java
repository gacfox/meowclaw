package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建工作区文件或目录请求")
public class CreateEntryRequest {
    @Schema(description = "工作区条目类型")
    public enum Type {FILE, DIR}

    @NotNull(message = "智能体ID不能为空")
    @Schema(description = "智能体 ID")
    private Long agentId;

    @NotNull(message = "路径不能为空")
    @Schema(description = "工作区相对路径")
    private String path;

    @NotNull(message = "类型不能为空")
    @Schema(description = "工作区条目类型")
    private Type type;
}
