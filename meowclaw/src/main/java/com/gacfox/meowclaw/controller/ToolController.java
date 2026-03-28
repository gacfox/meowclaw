package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.ToolDto;
import com.gacfox.meowclaw.service.ToolService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tools")
public class ToolController {
    private final ToolService toolService;

    public ToolController(ToolService toolService) {
        this.toolService = toolService;
    }

    @GetMapping
    public ApiResponse<List<ToolDto>> listTools() {
        return ApiResponse.success(toolService.listTools());
    }
}

