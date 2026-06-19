package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.TokenStatsDTO;
import com.gacfox.meowclaw.service.TokenUsageLogService;
import com.gacfox.proarc.common.model.ApiResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tokens")
public class TokenUsageController {

    private final TokenUsageLogService tokenUsageLogService;

    @Autowired
    public TokenUsageController(TokenUsageLogService tokenUsageLogService) {
        this.tokenUsageLogService = tokenUsageLogService;
    }

    /**
     * 查询Tokens统计报表
     *
     * @param start 起始时间戳毫秒（含）
     * @param end   结束时间戳毫秒（含）
     * @param llmId 模型ID，不传表示全部模型
     */
    @GetMapping("/stats")
    public ApiResult<TokenStatsDTO> stats(@RequestParam long start,
                                          @RequestParam long end,
                                          @RequestParam(required = false) Long llmId) {
        return ApiResult.success(tokenUsageLogService.stats(start, end, llmId));
    }
}
