package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Page<Conversation> findByAgentIdOrderByUpdatedAtDesc(Long agentId, Pageable pageable);

    Page<Conversation> findByAgentIdAndTypeOrderByUpdatedAtDesc(Long agentId, String type, Pageable pageable);
}
