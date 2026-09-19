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
 * LLM调用记录，每次LLM调用一条，同时服务于tokens统计与追踪观测
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mc_llm_call_log")
public class LlmCallLog {
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

    @Column(name = "purpose", length = 32)
    private String purpose;

    @Column(name = "request_messages", columnDefinition = "TEXT")
    private String requestMessages;

    @Column(name = "response_content", columnDefinition = "TEXT")
    private String responseContent;

    @Column(name = "response_tool_calls", columnDefinition = "TEXT")
    private String responseToolCalls;

    @Column(name = "reasoning_content", columnDefinition = "TEXT")
    private String reasoningContent;

    @Column(name = "input_tokens", nullable = false)
    private Long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private Long outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private Long totalTokens;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
