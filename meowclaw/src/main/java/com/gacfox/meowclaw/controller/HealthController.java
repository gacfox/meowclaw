package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 前端健康指示器
 */
@Tag(name = "健康检查")
@RestController
@RequestMapping("/api/health")
public class HealthController {
    @Operation(summary = "检查服务健康状态")
    @GetMapping("/echo")
    public ApiResult<Map<String, Long>> echo() {
        return ApiResult.success(Map.of("timestamp", Instant.now().toEpochMilli()));
    }
}
