package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.EmbeddingModelDTO;
import com.gacfox.meowclaw.entity.EmbeddingModel;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmbeddingModelConverter {

    EmbeddingModelDTO toDTO(EmbeddingModel entity);

    EmbeddingModel toEntity(EmbeddingModelDTO dto);
}
