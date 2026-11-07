package com.gacfox.meowclaw.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mc_conversation")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "title", length = 500)
    private String title;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "context_json")
    private String contextJson;

    @Column(name = "guardrail_policy_id")
    private Long guardrailPolicyId;

    @Column(name = "parent_conversation_id")
    private Long parentConversationId;

    @Column(name = "allowed_tools")
    private String allowedTools;

    @Column(name = "pinned", nullable = false)
    private Boolean pinned = false;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "created_at", nullable = false)
    private Long createdAt;

    @Column(name = "updated_at", nullable = false)
    private Long updatedAt;
}
