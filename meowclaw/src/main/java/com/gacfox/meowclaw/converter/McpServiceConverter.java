package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.mcp.McpServiceDTO;
import com.gacfox.meowclaw.entity.McpServiceConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface McpServiceConverter {
    @Mapping(target = "tools", ignore = true)
    McpServiceDTO toDTO(McpServiceConfig entity);

    McpServiceConfig toEntity(McpServiceDTO dto);
}
