package com.gacfox.meowclaw.repository;

import com.gacfox.meowclaw.entity.GuardrailPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GuardrailPolicyRepository extends JpaRepository<GuardrailPolicy, Long> {
    Optional<GuardrailPolicy> findByName(String name);

    boolean existsByName(String name);
}
