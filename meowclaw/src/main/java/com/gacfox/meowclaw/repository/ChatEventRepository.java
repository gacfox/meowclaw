package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.ChatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatEventRepository extends JpaRepository<ChatEvent, Long> {
    List<ChatEvent> findByBatchIdOrderByEventOrderAsc(Long batchId);

    void deleteByBatchIdIn(List<Long> batchIds);
}
