package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.SystemResourceMetricsDTO;
import com.gacfox.meowclaw.dto.SystemResourceMonitorEnabledRequest;
import com.gacfox.meowclaw.service.SystemResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "系统资源监控")
@RestController
@RequestMapping("/api/system-resource")
public class SystemResourceController {
    private final SystemResourceService systemResourceService;

    @Autowired
    public SystemResourceController(SystemResourceService systemResourceService) {
        this.systemResourceService = systemResourceService;
    }

    @Operation(summary = "获取服务端系统资源占用")
    @GetMapping("/metrics")
    public ApiResult<SystemResourceMetricsDTO> getMetrics() {
        return ApiResult.success(systemResourceService.getMetrics());
    }

    @Operation(summary = "查询系统资源监控开关")
    @GetMapping("/enabled")
    public ApiResult<Boolean> getEnabled() {
        return ApiResult.success(systemResourceService.isEnabled());
    }

    @Operation(summary = "修改系统资源监控开关")
    @PutMapping("/enabled")
    public ApiResult<Boolean> updateEnabled(@Valid @RequestBody SystemResourceMonitorEnabledRequest request) {
        return ApiResult.success(systemResourceService.updateEnabled(request.getEnabled()));
    }
}
