package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.EmbeddingModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModel, Long> {
}
