package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatEventBatchConverter {
    ChatEventBatchDTO toDTO(ChatEventBatch entity);
    ChatEventBatch toEntity(ChatEventBatchDTO dto);
}
