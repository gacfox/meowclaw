package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.chat.ChatEventDTO;
import com.gacfox.meowclaw.entity.ChatEvent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ChatEventConverter {
    ChatEventDTO toDTO(ChatEvent entity);

    ChatEvent toEntity(ChatEventDTO dto);
}
