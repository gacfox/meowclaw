package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * 前端健康指示器
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {
    @GetMapping("/echo")
    public ApiResult<Map<String, Long>> echo() {
        return ApiResult.success(Map.of("timestamp", Instant.now().toEpochMilli()));
    }
}
