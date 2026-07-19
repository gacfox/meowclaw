package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.MemoryNodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MemoryNodeEntityRepository extends JpaRepository<MemoryNodeEntity, Long> {

    List<MemoryNodeEntity> findByNodeIdIn(Collection<Long> nodeIds);

    List<MemoryNodeEntity> findByNodeId(Long nodeId);

    @Modifying
    @Query("update MemoryNodeEntity r set r.lastAccessedAt = :ts where r.id in :ids")
    void updateLastAccessedIn(@Param("ids") Collection<Long> ids, @Param("ts") Long ts);

    @Modifying
    @Query("delete MemoryNodeEntity r where r.nodeId = :nodeId")
    void deleteByNodeId(@Param("nodeId") Long nodeId);

    @Query("select count(r) > 0 from MemoryNodeEntity r where r.entityId = :entityId")
    boolean existsByEntityId(@Param("entityId") Long entityId);
}
