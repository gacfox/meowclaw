package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ToolInfoDTO;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import com.gacfox.proarc.common.model.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tool")
public class ToolController {
    private final ToolRegistry toolRegistry;

    @Autowired
    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public ApiResult<List<ToolInfoDTO>> list() {
        List<ToolInfoDTO> tools = toolRegistry.getAllTools().stream()
                .filter(def -> !def.getToolName().contains("__"))
                .map(def -> new ToolInfoDTO(def.getToolName(), def.getDescription()))
                .toList();
        return ApiResult.success(tools);
    }
}
