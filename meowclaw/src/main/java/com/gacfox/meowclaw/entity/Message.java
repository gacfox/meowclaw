package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class Message {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private byte[] embedding;
    private Long inputTokens;
    private Long outputTokens;
    private Long createdAt;

    public Instant getCreatedAtInstant() {
        return Instant.ofEpochMilli(createdAt);
    }

    public void setCreatedAtInstant(Instant instant) {
        this.createdAt = instant.toEpochMilli();
    }
}
