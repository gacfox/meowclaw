package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.Llm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmRepository extends JpaRepository<Llm, Long> {
}
