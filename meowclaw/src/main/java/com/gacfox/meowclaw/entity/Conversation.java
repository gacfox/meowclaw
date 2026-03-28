package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class Conversation {
    private Long id;
    private Long agentConfigId;
    private String title;
    private Long createdAt;
    private Long updatedAt;

    public Instant getCreatedAtInstant() {
        return Instant.ofEpochMilli(createdAt);
    }

    public void setCreatedAtInstant(Instant instant) {
        this.createdAt = instant.toEpochMilli();
    }

    public Instant getUpdatedAtInstant() {
        return Instant.ofEpochMilli(updatedAt);
    }

    public void setUpdatedAtInstant(Instant instant) {
        this.updatedAt = instant.toEpochMilli();
    }
}
