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
@Table(name = "mc_chat_event")
public class ChatEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "event_order", nullable = false)
    private Integer eventOrder;

    @Column(name = "type", nullable = false, length = 20)
    private String type;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_name", length = 200)
    private String toolName;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_arguments", columnDefinition = "TEXT")
    private String toolArguments;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;
}
