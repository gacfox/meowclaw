package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.ToolInfoDTO;
import com.gacfox.proarc.agentic.tool.ToolRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工具管理
 */
@Tag(name = "工具")
@RestController
@RequestMapping("/api/tool")
public class ToolController {
    private final ToolRegistry toolRegistry;

    @Autowired
    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Operation(summary = "查询可用工具列表")
    @GetMapping
    public ApiResult<List<ToolInfoDTO>> list() {
        List<ToolInfoDTO> tools = toolRegistry.getAllTools().stream()
                .filter(def -> !def.getToolName().contains("__"))
                .map(def -> new ToolInfoDTO(def.getToolName(), def.getDescription()))
                .toList();
        return ApiResult.success(tools);
    }
}
