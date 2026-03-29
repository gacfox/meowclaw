package com.gacfox.meowclaw.dto;

import lombok.Data;

@Data
public class MessageDto {
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_TOOL = "tool";
    private Long id;
    private String role;
    private String content;
    private Long timestamp;
    private Long inputTokens;
    private Long outputTokens;
    private String apiUrl;
    private String model;
}
