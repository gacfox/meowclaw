package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.MemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MemoryEntityRepository extends JpaRepository<MemoryEntity, Long> {

    long countByAgentId(Long agentId);

    List<MemoryEntity> findByAgentId(Long agentId);

    List<MemoryEntity> findByAgentIdAndNameIn(Long agentId, Collection<String> names);

    @Modifying
    @Query("update MemoryEntity e set e.lastAccessedAt = :ts where e.id in :ids")
    void updateLastAccessedIn(@Param("ids") Collection<Long> ids, @Param("ts") Long ts);
}
