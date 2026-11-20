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
@Table(name = "mc_agent_subagent", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_subagent_parent_subagent", columnNames = {"parent_agent_id", "subagent_id"})
})
public class AgentSubagentRelation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_agent_id", nullable = false)
    private Long parentAgentId;

    @Column(name = "subagent_id", nullable = false)
    private Long subagentId;
}
