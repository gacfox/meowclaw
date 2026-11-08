package com.gacfox.meowclaw.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * LLM调用Token用量事实，独立于可清理的追踪明细
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mc_token_usage")
public class TokenUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "llm_id")
    private Long llmId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "purpose", length = 32)
    private String purpose;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private Long totalTokens;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
