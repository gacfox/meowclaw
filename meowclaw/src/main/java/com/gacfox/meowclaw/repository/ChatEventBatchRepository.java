package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ChatEventBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatEventBatchRepository extends JpaRepository<ChatEventBatch, Long> {
    List<ChatEventBatch> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
