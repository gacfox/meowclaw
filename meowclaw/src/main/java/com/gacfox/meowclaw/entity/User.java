package com.gacfox.meowclaw.entity;

import lombok.Data;

import java.time.Instant;

@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String displayUsername;
    private String avatarUrl;
    private Long createdAt;

    public Instant getCreatedAtInstant() {
        return Instant.ofEpochMilli(createdAt);
    }

    public void setCreatedAtInstant(Instant instant) {
        this.createdAt = instant.toEpochMilli();
    }
}
