package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ConversationDTO;
import com.gacfox.meowclaw.entity.Conversation;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConversationConverter {

    ConversationDTO toDTO(Conversation entity);

    Conversation toEntity(ConversationDTO dto);
}
