package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AgentConverter {
    AgentDTO toDTO(Agent entity);

    @Mapping(target = "environmentVariablesJson", ignore = true)
    Agent toEntity(AgentDTO dto);
}
