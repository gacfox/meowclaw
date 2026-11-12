package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.Pagination;
import com.gacfox.meowclaw.dto.TraceDetailDTO;
import com.gacfox.meowclaw.dto.TraceItemDTO;
import com.gacfox.meowclaw.dto.TraceObservationEnabledRequest;
import com.gacfox.meowclaw.dto.TraceRetentionPolicy;
import com.gacfox.meowclaw.dto.TraceRetentionPolicyRequest;
import com.gacfox.meowclaw.scheduler.TraceRetentionScheduler;
import com.gacfox.meowclaw.service.TraceObservationService;
import com.gacfox.meowclaw.service.TraceRetentionService;
import com.gacfox.meowclaw.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 追踪管理
 */
@Tag(name = "调用追踪")
@RestController
@RequestMapping("/api/trace")
public class TraceController {
    private final TraceService traceService;
    private final TraceObservationService traceObservationService;
    private final TraceRetentionService traceRetentionService;
    private final TraceRetentionScheduler traceRetentionScheduler;

    @Autowired
    public TraceController(TraceService traceService, TraceObservationService traceObservationService,
                           TraceRetentionService traceRetentionService,
                           TraceRetentionScheduler traceRetentionScheduler) {
        this.traceService = traceService;
        this.traceObservationService = traceObservationService;
        this.traceRetentionService = traceRetentionService;
        this.traceRetentionScheduler = traceRetentionScheduler;
    }

    @Operation(summary = "查询调用追踪列表")
    @GetMapping
    public ApiResult<Pagination<TraceItemDTO>> list(@Parameter(description = "会话 ID") @RequestParam(required = false) Long conversationId,
                                                    @Parameter(description = "智能体 ID") @RequestParam(required = false) Long agentId,
                                                    @Parameter(description = "执行状态") @RequestParam(required = false) String status,
                                                    @Parameter(description = "追踪内容搜索关键词") @RequestParam(required = false) String keyword,
                                                    @Parameter(description = "开始时间戳（毫秒）") @RequestParam(required = false) Long startTime,
                                                    @Parameter(description = "结束时间戳（毫秒）") @RequestParam(required = false) Long endTime,
                                                    @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
                                                    @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(traceService.list(
                conversationId, agentId, status, keyword, startTime, endTime, page, size));
    }

    @Operation(summary = "查询调用追踪详情")
    @GetMapping("/{batchId}")
    public ApiResult<TraceDetailDTO> detail(@Parameter(description = "消息批次 ID") @PathVariable Long batchId) {
        return ApiResult.success(traceService.detail(batchId));
    }

    @Operation(summary = "删除调用追踪")
    @DeleteMapping("/{batchId}")
    public ApiResult<Integer> delete(@Parameter(description = "消息批次 ID") @PathVariable Long batchId) {
        return ApiResult.success(traceService.delete(batchId));
    }

    @Operation(summary = "批量删除调用追踪")
    @DeleteMapping("/batch")
    public ApiResult<Integer> deleteBatch(@RequestBody List<Long> batchIds) {
        return ApiResult.success(traceService.deleteBatch(batchIds));
    }

    @Operation(summary = "清空调用追踪")
    @DeleteMapping("/all")
    public ApiResult<?> deleteAll() {
        traceService.deleteAll();
        return ApiResult.success();
    }

    @Operation(summary = "查询追踪观测开关")
    @GetMapping("/enabled")
    public ApiResult<Boolean> getObservationEnabled() {
        return ApiResult.success(traceObservationService.isEnabled());
    }

    @Operation(summary = "修改追踪观测开关")
    @PutMapping("/enabled")
    public ApiResult<Boolean> updateObservationEnabled(@Valid @RequestBody TraceObservationEnabledRequest request) {
        return ApiResult.success(traceObservationService.updateEnabled(request.getEnabled()));
    }

    @Operation(summary = "查询追踪留存策略")
    @GetMapping("/retention-policy")
    public ApiResult<TraceRetentionPolicy> getRetentionPolicy() {
        return ApiResult.success(traceRetentionService.getPolicy());
    }

    @Operation(summary = "修改追踪留存策略")
    @PutMapping("/retention-policy")
    public ApiResult<TraceRetentionPolicy> updateRetentionPolicy(@Valid @RequestBody TraceRetentionPolicyRequest request) {
        TraceRetentionPolicy policy = traceRetentionService.updatePolicy(request.getPolicy());
        if (policy != TraceRetentionPolicy.ALL) {
            traceRetentionScheduler.cleanupAsync();
        }
        return ApiResult.success(policy);
    }
}
