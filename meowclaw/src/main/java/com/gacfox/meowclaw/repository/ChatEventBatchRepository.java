package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ChatEventBatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatEventBatchRepository extends JpaRepository<ChatEventBatch, Long> {
    List<ChatEventBatch> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    @Query("select b from ChatEventBatch b where b.type = 'USER'"
            + " and (:conversationId is null or b.conversationId = :conversationId)"
            + " and (:agentId is null or b.conversationId in"
            + " (select c.id from Conversation c where c.agentId = :agentId))"
            + " and (:status is null or b.status = :status)"
            + " and (:keyword is null or b.userContent like :keyword)"
            + " and (:startTime is null or b.createdAt >= :startTime)"
            + " and (:endTime is null or b.createdAt <= :endTime)"
            + " order by b.createdAt desc")
    Page<ChatEventBatch> searchTraces(@Param("conversationId") Long conversationId,
                                      @Param("agentId") Long agentId,
                                      @Param("status") String status,
                                      @Param("keyword") String keyword,
                                      @Param("startTime") Long startTime,
                                      @Param("endTime") Long endTime,
                                      Pageable pageable);
}
