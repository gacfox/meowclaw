package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.agent.tool.Tool;
import com.gacfox.meowclaw.agent.tool.ToolRegistry;
import com.gacfox.meowclaw.dto.ToolDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ToolService {
    private final ToolRegistry toolRegistry;

    public ToolService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<ToolDto> listTools() {
        return toolRegistry.listTools().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ToolDto toDto(Tool tool) {
        ToolDto dto = new ToolDto();
        dto.setId(tool.getName());
        dto.setName(tool.getName());
        dto.setDescription(tool.getDescription());
        return dto;
    }
}

