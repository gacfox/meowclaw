package com.gacfox.meowclaw.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventBatchDTO {
    private Long id;
    private Long conversationId;
    private String userContent;
    private String type;
    private String status;
    private String errorMessage;
    private Long inputTokens;
    private Long outputTokens;
    private Long createdAt;
    private Long completedAt;
    private List<ChatEventDTO> events;
}
