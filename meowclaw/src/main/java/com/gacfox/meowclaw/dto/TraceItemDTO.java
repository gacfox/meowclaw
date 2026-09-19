package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TraceItemDTO {
    private Long batchId;
    private Long conversationId;
    private String conversationTitle;
    private Long agentId;
    private String agentName;
    private String userContent;
    private String status;
    private Long inputTokens;
    private Long outputTokens;
    private Long llmCallCount;
    private Long createdAt;
    private Long completedAt;
}
