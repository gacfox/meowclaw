package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ConversationHistoryDTO;
import com.gacfox.meowclaw.entity.Conversation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConversationHistoryConverter {

    @Mapping(target = "agentName", source = "agentName")
    ConversationHistoryDTO toDTO(Conversation entity, String agentName);
}
