package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.TraceDetailDTO;
import com.gacfox.meowclaw.dto.TraceItemDTO;
import com.gacfox.meowclaw.service.TraceService;
import com.gacfox.proarc.common.model.ApiResult;
import com.gacfox.proarc.common.model.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trace")
public class TraceController {

    private final TraceService traceService;

    @Autowired
    public TraceController(TraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public ApiResult<Pagination<TraceItemDTO>> list(@RequestParam(required = false) Long conversationId,
                                                    @RequestParam(required = false) Long agentId,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) Long startTime,
                                                    @RequestParam(required = false) Long endTime,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(traceService.list(
                conversationId, agentId, status, keyword, startTime, endTime, page, size));
    }

    @GetMapping("/{batchId}")
    public ApiResult<TraceDetailDTO> detail(@PathVariable Long batchId) {
        return ApiResult.success(traceService.detail(batchId));
    }
}
