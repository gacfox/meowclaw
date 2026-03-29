package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.DailyStatisticsDto;
import com.gacfox.meowclaw.dto.StatisticsOverviewDto;
import com.gacfox.meowclaw.service.StatisticsService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<StatisticsOverviewDto> getOverview(
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        if (startTime == null || endTime == null) {
            endTime = Instant.now().toEpochMilli();
            startTime = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        }
        
        return ApiResponse.success(statisticsService.getOverview(startTime, endTime));
    }

    @GetMapping("/daily")
    public ApiResponse<List<DailyStatisticsDto>> getDailyStatistics(
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(required = false) String apiUrl,
            @RequestParam(required = false) String model) {
        
        if (startTime == null || endTime == null) {
            endTime = Instant.now().toEpochMilli();
            startTime = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli();
        }
        
        return ApiResponse.success(statisticsService.getDailyStatistics(startTime, endTime, apiUrl, model));
    }

    @GetMapping("/models")
    public ApiResponse<List<Map<String, String>>> getAvailableModels() {
        return ApiResponse.success(statisticsService.getAvailableModels());
    }
}