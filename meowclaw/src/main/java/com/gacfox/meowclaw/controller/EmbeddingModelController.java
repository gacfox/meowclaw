package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.EmbeddingModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 嵌入模型配置管理
 */
@Tag(name = "嵌入模型")
@RestController
@RequestMapping("/api/embedding-model")
public class EmbeddingModelController {
    private final EmbeddingModelService embeddingModelService;

    @Autowired
    public EmbeddingModelController(EmbeddingModelService embeddingModelService) {
        this.embeddingModelService = embeddingModelService;
    }

    @Operation(summary = "查询嵌入模型列表")
    @GetMapping
    public ApiResult<List<EmbeddingModelDTO>> list() {
        return ApiResult.success(embeddingModelService.list());
    }

    @Operation(summary = "创建嵌入模型")
    @PostMapping
    public ApiResult<EmbeddingModelDTO> create(@RequestBody @Valid CreateEmbeddingModelRequest req) {
        return ApiResult.success(embeddingModelService.create(req));
    }

    @Operation(summary = "更新嵌入模型")
    @PutMapping("/{id}")
    public ApiResult<EmbeddingModelDTO> update(@Parameter(description = "嵌入模型 ID") @PathVariable Long id,
                                               @RequestBody @Valid UpdateEmbeddingModelRequest req) {
        return ApiResult.success(embeddingModelService.update(id, req));
    }

    @Operation(summary = "删除嵌入模型")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "嵌入模型 ID") @PathVariable Long id) {
        embeddingModelService.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "测试嵌入模型连接")
    @PostMapping("/test")
    public ApiResult<EmbeddingTestResultDTO> test(@RequestBody @Valid EmbeddingModelTestRequest req) {
        return ApiResult.success(embeddingModelService.test(req));
    }
}
