package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.agent.AgentDTO;
import com.gacfox.meowclaw.entity.Agent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AgentConverter {
    @Mapping(target = "professionalSubagentIds", ignore = true)
    AgentDTO toDTO(Agent entity);

    Agent toEntity(AgentDTO dto);
}
