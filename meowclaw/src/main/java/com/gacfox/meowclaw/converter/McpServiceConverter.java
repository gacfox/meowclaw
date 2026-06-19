package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.McpServiceDTO;
import com.gacfox.meowclaw.entity.McpServiceConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface McpServiceConverter {

    @Mapping(target = "tools", ignore = true)
    McpServiceDTO toDTO(McpServiceConfig entity);

    McpServiceConfig toEntity(McpServiceDTO dto);
}
