package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.agent.mcp.McpClientManager;
import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.McpClientStatusDto;
import com.gacfox.meowclaw.dto.McpConfigDto;
import com.gacfox.meowclaw.entity.McpConfig;
import com.gacfox.meowclaw.repository.McpConfigRepository;
import com.gacfox.meowclaw.service.McpConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mcp-configs")
public class McpConfigController {
    private final McpConfigService mcpConfigService;
    private final McpClientManager mcpClientManager;
    private final McpConfigRepository mcpConfigRepository;

    public McpConfigController(McpConfigService mcpConfigService, McpClientManager mcpClientManager, McpConfigRepository mcpConfigRepository) {
        this.mcpConfigService = mcpConfigService;
        this.mcpClientManager = mcpClientManager;
        this.mcpConfigRepository = mcpConfigRepository;
    }

    @GetMapping
    public ApiResponse<List<McpConfigDto>> list() {
        return ApiResponse.success(mcpConfigService.findAll());
    }

    @GetMapping("/status")
    public ApiResponse<List<McpClientStatusDto>> getClientStatus() {
        return ApiResponse.success(mcpClientManager.getAllClientStatus());
    }

    @PostMapping("/{id}/reinitialize")
    public ApiResponse<McpClientStatusDto> reinitialize(@PathVariable Long id) {
        McpConfig mcpConfig = mcpConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP配置不存在"));
        mcpClientManager.reinitializeClient(mcpConfig);
        return ApiResponse.success(mcpClientManager.getAllClientStatus().stream()
                .filter(s -> s.getName().equals(mcpConfig.getName()))
                .findFirst()
                .orElse(null));
    }

    @GetMapping("/{id}")
    public ApiResponse<McpConfigDto> getById(@PathVariable Long id) {
        return ApiResponse.success(mcpConfigService.findById(id));
    }

    @PostMapping
    public ApiResponse<McpConfigDto> create(@Valid @RequestBody McpConfigDto dto) {
        McpConfigDto result = mcpConfigService.create(dto);
        McpConfig mcpConfig = mcpConfigService.findByName(dto.getName());
        if (mcpConfig != null) {
            mcpClientManager.initializeClient(mcpConfig);
        }
        return ApiResponse.success(result);
    }

    @PutMapping("/{id}")
    public ApiResponse<McpConfigDto> update(@PathVariable Long id, @Valid @RequestBody McpConfigDto dto) {
        McpConfigDto result = mcpConfigService.update(id, dto);
        McpConfig mcpConfig = mcpConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP配置不存在"));
        if (mcpConfig != null) {
            mcpClientManager.reinitializeClient(mcpConfig);
        }
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        McpConfig mcpConfig = mcpConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP配置不存在"));
        if (mcpConfig != null) {
            mcpClientManager.removeClient(mcpConfig.getName());
        }
        mcpConfigService.delete(id);
        return ApiResponse.success();
    }
}
