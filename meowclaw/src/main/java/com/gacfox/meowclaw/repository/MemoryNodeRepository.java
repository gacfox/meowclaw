package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.MemoryNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MemoryNodeRepository extends JpaRepository<MemoryNode, Long> {

    long countByAgentId(Long agentId);

    List<MemoryNode> findByAgentIdAndIdIn(Long agentId, Collection<Long> ids);

    @Modifying
    @Query("update MemoryNode n set n.lastAccessedAt = :ts where n.id in :ids")
    void updateLastAccessedIn(@Param("ids") Collection<Long> ids, @Param("ts") Long ts);
}
