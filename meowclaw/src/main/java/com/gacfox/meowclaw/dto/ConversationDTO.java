package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话信息")
public class ConversationDTO {
    @Schema(description = "会话 ID")
    private Long id;
    @Schema(description = "智能体 ID")
    private Long agentId;
    @Schema(description = "会话标题")
    private String title;
    @Schema(description = "会话类型")
    private String type;
    @Schema(description = "护栏策略 ID")
    private Long guardrailPolicyId;
    @Schema(description = "是否置顶")
    private Boolean pinned;
    @Schema(description = "项目 ID")
    private Long projectId;
    @Schema(description = "上下文提示词 Token 数")
    private Long contextPromptTokens;
    @Schema(description = "上下文窗口长度")
    private Integer contextLength;
    @Schema(description = "创建时间戳")
    private Long createdAt;
    @Schema(description = "更新时间戳")
    private Long updatedAt;
}
