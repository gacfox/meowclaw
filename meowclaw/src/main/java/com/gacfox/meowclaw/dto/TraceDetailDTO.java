package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceDetailDTO {
    private Long batchId;
    private Long conversationId;
    private String conversationTitle;
    private Long agentId;
    private String agentName;
    private String userContent;
    private List<ChatAttachmentDTO> attachments;
    private String status;
    private String errorMessage;
    private Long inputTokens;
    private Long outputTokens;
    private Long createdAt;
    private Long completedAt;
    private List<ChatEventDTO> events;
    private List<LlmCallLogDTO> llmCalls;
}
