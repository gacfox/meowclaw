package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    void deleteByBatchIdIn(List<Long> batchIds);
}
