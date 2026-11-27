package com.gacfox.meowclaw.controller.observability;

import com.gacfox.meowclaw.dto.common.ApiResult;
import com.gacfox.meowclaw.dto.observability.SystemResourceMetricsDTO;
import com.gacfox.meowclaw.dto.observability.SystemResourceMonitorEnabledRequest;
import com.gacfox.meowclaw.service.observability.SystemResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
