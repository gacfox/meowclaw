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
@Schema(description = "会话消息")
public class MessageDTO {
    @Schema(description = "消息 ID")
    private Long id;
    @Schema(description = "会话 ID")
    private Long conversationId;
    @Schema(description = "消息批次 ID")
    private Long batchId;
    @Schema(description = "消息角色")
    private String role;
    @Schema(description = "消息内容")
    private String content;
    @Schema(description = "推理过程内容")
    private String reasoningContent;
    @Schema(description = "工具调用 JSON")
    private String toolCallsJson;
    @Schema(description = "工具调用 ID")
    private String toolCallId;
    @Schema(description = "输入 Token 数")
    private Long inputTokens;
    @Schema(description = "输出 Token 数")
    private Long outputTokens;
    @Schema(description = "消息创建时间戳")
    private Long createdAt;
}
