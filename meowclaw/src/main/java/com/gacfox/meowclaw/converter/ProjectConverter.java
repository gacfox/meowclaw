package com.gacfox.meowclaw.converter;

import com.gacfox.meowclaw.dto.project.ProjectDTO;
import com.gacfox.meowclaw.entity.Project;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProjectConverter {
    ProjectDTO toDTO(Project entity);
}
