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

/**
 * Tokens消耗明细，每次LLM调用一条
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mc_token_usage_log")
public class TokenUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "llm_id")
    private Long llmId;

    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "batch_id")
    private Long batchId;

    @Column(name = "model", length = 100)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private Long totalTokens;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
