package com.gacfox.meowclaw.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mc_environment_variable")
public class EnvironmentVariable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "key_name", nullable = false, length = 255)
    private String keyName;

    @Column(name = "key_value", nullable = false, columnDefinition = "TEXT")
    private String keyValue;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
