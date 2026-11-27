package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.guardrail.GuardrailPolicyDTO;
import com.gacfox.meowclaw.entity.GuardrailPolicy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GuardrailPolicyConverter {
    GuardrailPolicyDTO toDTO(GuardrailPolicy entity);
}
