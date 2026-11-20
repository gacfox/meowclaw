package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.AgentSubagentRelation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentSubagentRelationRepository extends JpaRepository<AgentSubagentRelation, Long> {
    List<AgentSubagentRelation> findByParentAgentId(Long parentAgentId);

    List<AgentSubagentRelation> findBySubagentId(Long subagentId);

    boolean existsByParentAgentIdAndSubagentId(Long parentAgentId, Long subagentId);

    void deleteByParentAgentId(Long parentAgentId);
}
