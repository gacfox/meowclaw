package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class McpConfig {
    private Long id;
    private String name;
    private String transportType;
    private String command;
    private String args;
    private String envVars;
    private String url;
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