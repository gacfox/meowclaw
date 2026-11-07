package com.gacfox.meowclaw.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.gacfox.meowclaw.dto.ChatAttachmentDTO;
import com.gacfox.meowclaw.util.JsonUtil;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ChatAttachmentConverter {
    private final JsonUtil jsonUtil;

    @Autowired
    public ChatAttachmentConverter(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    @Named("jsonToAttachments")
    public List<ChatAttachmentDTO> jsonToAttachments(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return jsonUtil.load(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
