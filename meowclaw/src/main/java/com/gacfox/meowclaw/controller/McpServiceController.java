package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateMcpServiceRequest;
import com.gacfox.meowclaw.dto.McpServiceDTO;
import com.gacfox.meowclaw.dto.McpServiceTestRequest;
import com.gacfox.meowclaw.dto.McpTestResultDTO;
import com.gacfox.meowclaw.dto.McpToolDTO;
import com.gacfox.meowclaw.dto.UpdateMcpServiceRequest;
import com.gacfox.meowclaw.service.McpService;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/mcp-service")
public class McpServiceController {
    private final McpService manager;

    public McpServiceController(McpService manager) {
        this.manager = manager;
    }

    @GetMapping
    public ApiResult<List<McpServiceDTO>> list() {
        return ApiResult.success(manager.list());
    }

    @PostMapping
    public ApiResult<McpServiceDTO> create(@RequestBody @Valid CreateMcpServiceRequest req) {
        return ApiResult.success(manager.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<McpServiceDTO> update(@PathVariable Long id, @RequestBody @Valid UpdateMcpServiceRequest req) {
        return ApiResult.success(manager.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        manager.delete(id);
        return ApiResult.success();
    }

    @PutMapping("/{id}/toggle")
    public ApiResult<McpServiceDTO> toggle(@PathVariable Long id) {
        return ApiResult.success(manager.toggleEnabled(id));
    }

    @PostMapping("/test")
    public ApiResult<McpTestResultDTO> test(@RequestBody @Valid McpServiceTestRequest req) {
        return ApiResult.success(manager.test(req));
    }

    @PostMapping("/{id}/refresh")
    public ApiResult<McpServiceDTO> refresh(@PathVariable Long id) {
        return ApiResult.success(manager.refreshStatus(id));
    }

    @GetMapping("/tool")
    public ApiResult<List<McpToolDTO>> listTools() {
        return ApiResult.success(manager.listEnabledTools());
    }
}
