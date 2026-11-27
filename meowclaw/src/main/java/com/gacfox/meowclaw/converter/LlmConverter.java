package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.model.LlmDTO;
import com.gacfox.meowclaw.entity.Llm;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmConverter {
    LlmDTO toDTO(Llm entity);

    Llm toEntity(LlmDTO dto);
}
