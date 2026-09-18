package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.MemoryNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Query("select n from MemoryNode n where n.agentId = :agentId"
            + " and (:type is null or n.type = :type)"
            + " and (:keyword is null or n.content like :keyword)"
            + " and (:entityId is null or exists (select 1 from MemoryNodeEntity r"
            + " where r.nodeId = n.id and r.entityId = :entityId))"
            + " order by n.updatedAt desc")
    Page<MemoryNode> search(@Param("agentId") Long agentId, @Param("type") String type,
                            @Param("keyword") String keyword, @Param("entityId") Long entityId,
                            Pageable pageable);

    @Modifying
    @Query("update MemoryNode n set n.lastAccessedAt = :ts where n.id in :ids")
    void updateLastAccessedIn(@Param("ids") Collection<Long> ids, @Param("ts") Long ts);
}
