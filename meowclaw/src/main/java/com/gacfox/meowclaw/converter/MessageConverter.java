package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.MessageDTO;
import com.gacfox.meowclaw.entity.Message;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageConverter {
    MessageDTO toDTO(Message entity);
    Message toEntity(MessageDTO dto);
}
