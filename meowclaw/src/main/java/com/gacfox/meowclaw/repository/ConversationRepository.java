package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Page<Conversation> findByAgentIdOrderByUpdatedAtDesc(Long agentId, Pageable pageable);

    Page<Conversation> findByAgentIdAndTypeOrderByUpdatedAtDesc(Long agentId, String type, Pageable pageable);

    @Query("SELECT c FROM Conversation c "
            + "WHERE (:type IS NULL OR c.type = :type) "
            + "AND (:agentId IS NULL OR c.agentId = :agentId) "
            + "AND (:keyword IS NULL OR c.title LIKE :keyword) "
            + "AND (:startTime IS NULL OR c.updatedAt >= :startTime) "
            + "AND (:endTime IS NULL OR c.updatedAt <= :endTime) "
            + "ORDER BY c.updatedAt DESC")
    Page<Conversation> findHistory(
            @Param("type") String type,
            @Param("agentId") Long agentId,
            @Param("keyword") String keyword,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            Pageable pageable);
}
