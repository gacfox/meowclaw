package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.UserDTO;
import com.gacfox.meowclaw.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserConverter {

    UserDTO toDTO(User entity);

    User toEntity(UserDTO dto);
}
