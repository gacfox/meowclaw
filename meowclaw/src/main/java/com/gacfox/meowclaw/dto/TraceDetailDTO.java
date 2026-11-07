package com.gacfox.meowclaw.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "调用追踪详情")
public class TraceDetailDTO {
    @Schema(description = "消息批次 ID")
    private Long batchId;
    @Schema(description = "会话 ID")
    private Long conversationId;
    @Schema(description = "会话标题")
    private String conversationTitle;
    @Schema(description = "智能体 ID")
    private Long agentId;
    @Schema(description = "智能体名称")
    private String agentName;
    @Schema(description = "用户消息内容")
    private String userContent;
    @Schema(description = "聊天附件列表")
    private List<ChatAttachmentDTO> attachments;
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
    @Schema(description = "语言模型调用日志列表")
    private List<LlmCallLogDTO> llmCalls;
}
