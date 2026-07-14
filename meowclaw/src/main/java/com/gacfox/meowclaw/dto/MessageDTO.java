package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private Long batchId;
    private String role;
    private String content;
    private String reasoningContent;
    private String toolCallsJson;
    private String toolCallId;
    private Long inputTokens;
    private Long outputTokens;
    private Long createdAt;
}
