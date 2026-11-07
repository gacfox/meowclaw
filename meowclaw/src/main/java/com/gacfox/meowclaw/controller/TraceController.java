package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.Pagination;
import com.gacfox.meowclaw.dto.TraceDetailDTO;
import com.gacfox.meowclaw.dto.TraceItemDTO;
import com.gacfox.meowclaw.service.TraceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 追踪管理
 */
@Tag(name = "调用追踪")
@RestController
@RequestMapping("/api/trace")
public class TraceController {
    private final TraceService traceService;

    @Autowired
    public TraceController(TraceService traceService) {
        this.traceService = traceService;
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
}
