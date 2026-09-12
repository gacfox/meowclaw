package com.gacfox.meowclaw.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.dto.ChatAttachmentDTO;
import com.gacfox.meowclaw.dto.ChatEventBatchDTO;
import com.gacfox.meowclaw.entity.ChatEventBatch;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatEventBatchConverter {
    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "attachments", source = "attachments", qualifiedByName = "jsonToAttachments")
    ChatEventBatchDTO toDTO(ChatEventBatch entity);

    @Mapping(target = "attachments", ignore = true)
    ChatEventBatch toEntity(ChatEventBatchDTO dto);

    @Named("jsonToAttachments")
    static List<ChatAttachmentDTO> jsonToAttachments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
