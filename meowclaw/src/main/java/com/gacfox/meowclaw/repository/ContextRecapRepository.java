package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ContextRecap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContextRecapRepository extends JpaRepository<ContextRecap, Long> {
    List<ContextRecap> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    boolean existsByConversationIdAndFromBatchIdAndToBatchIdAndType(Long conversationId, Long fromBatchId, Long toBatchId, String type);

    void deleteByConversationId(Long conversationId);

    void deleteByConversationIdAndFromBatchIdInOrConversationIdAndToBatchIdIn(Long conversationId, List<Long> fromBatchIds,
                                                                                Long conversationId2, List<Long> toBatchIds);
}
