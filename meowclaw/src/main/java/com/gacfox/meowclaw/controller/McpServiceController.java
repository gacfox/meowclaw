package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.McpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP 外置工具配置管理
 */
@Tag(name = "MCP 服务")
@RestController
@RequestMapping("/api/mcp-service")
public class McpServiceController {
    private final McpService manager;

    @Autowired
    public McpServiceController(McpService manager) {
        this.manager = manager;
    }

    @Operation(summary = "查询 MCP 服务列表")
    @GetMapping
    public ApiResult<List<McpServiceDTO>> list() {
        return ApiResult.success(manager.list());
    }

    @Operation(summary = "创建 MCP 服务")
    @PostMapping
    public ApiResult<McpServiceDTO> create(@RequestBody @Valid CreateMcpServiceRequest req) {
        return ApiResult.success(manager.create(req));
    }

    @Operation(summary = "更新 MCP 服务")
    @PutMapping("/{id}")
    public ApiResult<McpServiceDTO> update(@Parameter(description = "MCP 服务 ID") @PathVariable Long id, @RequestBody @Valid UpdateMcpServiceRequest req) {
        return ApiResult.success(manager.update(id, req));
    }

    @Operation(summary = "删除 MCP 服务")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "MCP 服务 ID") @PathVariable Long id) {
        manager.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "切换 MCP 服务启用状态")
    @PutMapping("/{id}/toggle")
    public ApiResult<McpServiceDTO> toggle(@Parameter(description = "MCP 服务 ID") @PathVariable Long id) {
        return ApiResult.success(manager.toggleEnabled(id));
    }

    @Operation(summary = "测试 MCP 服务连接")
    @PostMapping("/test")
    public ApiResult<McpTestResultDTO> test(@RequestBody @Valid McpServiceTestRequest req) {
        return ApiResult.success(manager.test(req));
    }

    @Operation(summary = "刷新 MCP 服务状态")
    @PostMapping("/{id}/refresh")
    public ApiResult<McpServiceDTO> refresh(@Parameter(description = "MCP 服务 ID") @PathVariable Long id) {
        return ApiResult.success(manager.refreshStatus(id));
    }

    @Operation(summary = "查询可用 MCP 工具")
    @GetMapping("/tool")
    public ApiResult<List<McpToolDTO>> listTools() {
        return ApiResult.success(manager.listEnabledTools());
    }
}
