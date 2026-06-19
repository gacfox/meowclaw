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
@Table(name = "mc_scheduled_task_execution")
public class ScheduledTaskExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheduled_task_id", nullable = false)
    private Long scheduledTaskId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "executed_at", nullable = false)
    private Long executedAt;
}
