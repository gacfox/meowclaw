package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建智能体请求")
public class CreateAgentRequest {
    @NotBlank(message = "智能体名称不能为空")
    @Size(max = 255, message = "智能体名称长度不能超过255")
    @Schema(description = "智能体名称")
    private String name;

    @Size(max = 500, message = "头像URL长度不能超过500")
    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "智能体人设")
    private String persona;

    @Size(max = 2000, message = "启用工具配置长度不能超过2000")
    @Schema(description = "启用的内置工具配置 JSON")
    private String enabledTools;

    @Size(max = 2000, message = "启用MCP工具配置长度不能超过2000")
    @Schema(description = "启用的 MCP 工具配置 JSON")
    private String enabledMcpTools;

    @NotNull(message = "请选择Agent执行LLM")
    @Schema(description = "主语言模型配置 ID")
    private Long llmId;

    @NotNull(message = "请选择辅助LLM")
    @Schema(description = "辅助语言模型配置 ID")
    private Long secondaryLlmId;

    @Schema(description = "向量模型配置 ID")
    private Long embeddingModelId;

    @Size(max = 500, message = "工作区路径长度不能超过500")
    @Schema(description = "工作区目录路径")
    private String workspaceFolder;

    @Schema(description = "是否启用积极记忆召回")
    private Boolean aggressiveMemoryRecall;
}
