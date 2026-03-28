package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.McpConfigDto;
import com.gacfox.meowclaw.service.McpConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp-configs")
public class McpConfigController {
    private final McpConfigService mcpConfigService;

    public McpConfigController(McpConfigService mcpConfigService) {
        this.mcpConfigService = mcpConfigService;
    }

    @GetMapping
    public ApiResponse<List<McpConfigDto>> list() {
        return ApiResponse.success(mcpConfigService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<McpConfigDto> getById(@PathVariable Long id) {
        return ApiResponse.success(mcpConfigService.findById(id));
    }

    @PostMapping
    public ApiResponse<McpConfigDto> create(@Valid @RequestBody McpConfigDto dto) {
        return ApiResponse.success(mcpConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<McpConfigDto> update(@PathVariable Long id, @Valid @RequestBody McpConfigDto dto) {
        return ApiResponse.success(mcpConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        mcpConfigService.delete(id);
        return ApiResponse.success();
    }
}