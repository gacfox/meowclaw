package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.EnvironmentVariableDTO;
import com.gacfox.meowclaw.entity.EnvironmentVariable;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EnvironmentVariableConverter {
    @Mapping(target = "key", source = "keyName")
    @Mapping(target = "value", source = "keyValue")
    EnvironmentVariableDTO toDTO(EnvironmentVariable entity);
}
