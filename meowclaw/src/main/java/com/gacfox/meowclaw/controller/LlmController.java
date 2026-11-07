package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.CreateLlmRequest;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.UpdateLlmRequest;
import com.gacfox.meowclaw.service.LlmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 大语言模型配置管理
 */
@Tag(name = "语言模型")
@RestController
@RequestMapping("/api/llm")
public class LlmController {
    private final LlmService llmService;

    @Autowired
    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }

    @Operation(summary = "查询语言模型列表")
    @GetMapping
    public ApiResult<List<LlmDTO>> list() {
        return ApiResult.success(llmService.list());
    }

    @Operation(summary = "创建语言模型")
    @PostMapping
    public ApiResult<LlmDTO> create(@RequestBody @Valid CreateLlmRequest req) {
        return ApiResult.success(llmService.create(req));
    }

    @Operation(summary = "更新语言模型")
    @PutMapping("/{id}")
    public ApiResult<LlmDTO> update(@Parameter(description = "语言模型 ID") @PathVariable Long id, @RequestBody @Valid UpdateLlmRequest req) {
        return ApiResult.success(llmService.update(id, req));
    }

    @Operation(summary = "删除语言模型")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "语言模型 ID") @PathVariable Long id) {
        llmService.delete(id);
        return ApiResult.success();
    }
}
