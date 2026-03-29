package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class ScheduledTask {
    private Long id;
    private String name;
    private Long agentConfigId;
    private String userPrompt;
    private String cronExpression;
    private boolean newSessionEach;
    private Long boundConversationId;
    private boolean enabled;
    private Long lastExecutedAt;
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

    public Instant getLastExecutedAtInstant() {
        return lastExecutedAt != null ? Instant.ofEpochMilli(lastExecutedAt) : null;
    }

    public void setLastExecutedAtInstant(Instant instant) {
        this.lastExecutedAt = instant != null ? instant.toEpochMilli() : null;
    }
}
