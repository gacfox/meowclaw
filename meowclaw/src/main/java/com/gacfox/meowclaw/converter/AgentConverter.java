package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.entity.Agent;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AgentConverter {

    AgentDTO toDTO(Agent entity);

    Agent toEntity(AgentDTO dto);
}
