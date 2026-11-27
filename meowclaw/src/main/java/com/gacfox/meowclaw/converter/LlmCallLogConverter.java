package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.observability.LlmCallLogDTO;
import com.gacfox.meowclaw.entity.LlmCallLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LlmCallLogConverter {
    LlmCallLogDTO toDTO(LlmCallLog entity);
}
