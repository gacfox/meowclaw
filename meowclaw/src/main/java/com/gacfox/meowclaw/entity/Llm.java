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
@Table(name = "mc_llm")
public class Llm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "endpoint_url", nullable = false, length = 500)
    private String endpointUrl;

    @Column(name = "sk")
    private String sk;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "max_tokens")
    private Integer maxTokens;

    @Column(name = "context_length")
    private Integer contextLength;

    @Column(name = "temperature")
    private Integer temperature;

    @Column(name = "capabilities", length = 255)
    private String capabilities;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
