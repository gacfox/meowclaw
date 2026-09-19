package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallLogDTO {
    private Long id;
    private String purpose;
    private String model;
    private String status;
    private String errorMessage;
    private Long inputTokens;
    private Long outputTokens;
    private Long totalTokens;
    private Long durationMs;
    private String requestMessages;
    private String responseContent;
    private String responseToolCalls;
    private String reasoningContent;
    private Long createdAt;
}
