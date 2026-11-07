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
@Schema(description = "聊天事件批次")
public class ChatEventBatchDTO {
    @Schema(description = "消息批次 ID")
    private Long id;
    @Schema(description = "会话 ID")
    private Long conversationId;
    @Schema(description = "用户消息内容")
    private String userContent;
    @Schema(description = "聊天附件列表")
    private List<ChatAttachmentDTO> attachments;
    @Schema(description = "消息批次类型")
    private String type;
    @Schema(description = "消息处理状态")
    private String status;
    @Schema(description = "错误信息")
    private String errorMessage;
    @Schema(description = "输入 Token 数")
    private Long inputTokens;
    @Schema(description = "输出 Token 数")
    private Long outputTokens;
    @Schema(description = "批次创建时间戳")
    private Long createdAt;
    @Schema(description = "批次完成时间戳")
    private Long completedAt;
    @Schema(description = "聊天事件列表")
    private List<ChatEventDTO> events;
}
