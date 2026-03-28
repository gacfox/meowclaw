package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class LlmConfig {
    private Long id;
    private String name;
    private String apiUrl;
    private String apiKey;
    private String model;
    private Integer maxContextLength;
    private Double temperature;
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
