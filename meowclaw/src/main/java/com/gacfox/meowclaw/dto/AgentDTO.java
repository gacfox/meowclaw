package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "智能体配置")
public class AgentDTO {
    @Schema(description = "智能体 ID")
    private Long id;
    @Schema(description = "智能体名称")
    private String name;
    @Schema(description = "头像地址")
    private String avatarUrl;
    @Schema(description = "智能体人设")
    private String persona;
    @Schema(description = "启用的内置工具配置 JSON")
    private String enabledTools;
    @Schema(description = "启用的 MCP 工具配置 JSON")
    private String enabledMcpTools;
    @Schema(description = "主语言模型配置 ID")
    private Long llmId;
    @Schema(description = "辅助语言模型配置 ID")
    private Long secondaryLlmId;
    @Schema(description = "向量模型配置 ID")
    private Long embeddingModelId;
    @Schema(description = "工作区目录路径")
    private String workspaceFolder;
    @Schema(description = "是否启用积极记忆召回")
    private Boolean aggressiveMemoryRecall;
    @Schema(description = "是否允许作为专业子Agent")
    private Boolean allowAsSubagent;
    @Schema(description = "子智能体描述提示词，用于主智能体编排调度")
    private String subagentDescription;
    @Schema(description = "专业子Agent ID列表")
    private List<Long> professionalSubagentIds;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
