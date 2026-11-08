package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.TokenStatsDTO;
import com.gacfox.meowclaw.service.TokenUsageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Token统计信息
 */
@Tag(name = "Token 统计")
@RestController
@RequestMapping("/api/tokens")
public class TokenUsageController {
    private final TokenUsageService tokenUsageService;

    @Autowired
    public TokenUsageController(TokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    /**
     * 查询Tokens统计报表
     *
     * @param start 起始时间戳毫秒（含）
     * @param end   结束时间戳毫秒（含）
     * @param llmId 模型ID，不传表示全部模型
     */
    @Operation(summary = "查询 Token 用量统计")
    @GetMapping("/stats")
    public ApiResult<TokenStatsDTO> stats(@Parameter(description = "起始时间戳（毫秒，含）") @RequestParam long start,
                                          @Parameter(description = "结束时间戳（毫秒，含）") @RequestParam long end,
                                          @Parameter(description = "语言模型 ID，不传则统计全部模型") @RequestParam(required = false) Long llmId) {
        return ApiResult.success(tokenUsageService.stats(start, end, llmId));
    }
}
