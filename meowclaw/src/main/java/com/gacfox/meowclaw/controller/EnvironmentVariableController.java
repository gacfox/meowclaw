package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.CreateEnvironmentVariableRequest;
import com.gacfox.meowclaw.dto.EnvironmentVariableDTO;
import com.gacfox.meowclaw.dto.UpdateEnvironmentVariableRequest;
import com.gacfox.meowclaw.service.EnvironmentVariableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "环境变量")
@RestController
@RequestMapping("/api/environment-variable")
public class EnvironmentVariableController {
    private final EnvironmentVariableService environmentVariableService;

    @Autowired
    public EnvironmentVariableController(EnvironmentVariableService environmentVariableService) {
        this.environmentVariableService = environmentVariableService;
    }

    @Operation(summary = "查询环境变量，指定智能体时同时返回全局变量")
    @GetMapping
    public ApiResult<List<EnvironmentVariableDTO>> list(
            @Parameter(description = "智能体 ID，空表示仅查询全局变量")
            @RequestParam(required = false) Long agentId) {
        return ApiResult.success(environmentVariableService.list(agentId));
    }

    @Operation(summary = "创建环境变量")
    @PostMapping
    public ApiResult<EnvironmentVariableDTO> create(@RequestBody @Valid CreateEnvironmentVariableRequest req) {
        return ApiResult.success(environmentVariableService.create(req));
    }

    @Operation(summary = "更新环境变量")
    @PutMapping("/{id}")
    public ApiResult<EnvironmentVariableDTO> update(
            @Parameter(description = "环境变量 ID") @PathVariable Long id,
            @RequestBody @Valid UpdateEnvironmentVariableRequest req) {
        return ApiResult.success(environmentVariableService.update(id, req));
    }

    @Operation(summary = "删除环境变量")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "环境变量 ID") @PathVariable Long id) {
        environmentVariableService.delete(id);
        return ApiResult.success();
    }
}
