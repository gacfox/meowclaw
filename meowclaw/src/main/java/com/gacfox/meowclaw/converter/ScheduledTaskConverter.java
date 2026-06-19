package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import com.gacfox.meowclaw.entity.ScheduledTask;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScheduledTaskConverter {

    ScheduledTaskDTO toDTO(ScheduledTask entity);

    ScheduledTask toEntity(ScheduledTaskDTO dto);
}
