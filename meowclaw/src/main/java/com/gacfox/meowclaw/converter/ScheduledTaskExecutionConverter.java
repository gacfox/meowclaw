package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.ScheduledTaskExecutionDTO;
import com.gacfox.meowclaw.entity.ScheduledTaskExecution;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ScheduledTaskExecutionConverter {

    ScheduledTaskExecutionDTO toDTO(ScheduledTaskExecution entity);
}
