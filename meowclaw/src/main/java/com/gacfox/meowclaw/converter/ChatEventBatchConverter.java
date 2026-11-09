package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = ChatAttachmentConverter.class)
public interface ChatEventBatchConverter {
    @Mapping(target = "attachments", source = "attachments", qualifiedByName = "jsonToAttachments")
    ChatEventBatchDTO toDTO(ChatEventBatch entity);

    @Mapping(target = "attachments", ignore = true)
    @Mapping(target = "promptContextJson", ignore = true)
    ChatEventBatch toEntity(ChatEventBatchDTO dto);
}
