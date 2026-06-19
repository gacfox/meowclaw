package com.gacfox.meowclaw.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mc_mcp_service_config")
public class McpServiceConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "protocol", nullable = false, length = 20)
    private String protocol;

    @Column(name = "config", nullable = false, columnDefinition = "TEXT")
    private String config;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "tools_cache", columnDefinition = "TEXT")
    private String toolsCache;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_checked_at")
    private Long lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
