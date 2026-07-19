package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateEmbeddingModelRequest;
import com.gacfox.meowclaw.dto.EmbeddingModelDTO;
import com.gacfox.meowclaw.dto.EmbeddingModelTestRequest;
import com.gacfox.meowclaw.dto.EmbeddingTestResultDTO;
import com.gacfox.meowclaw.dto.UpdateEmbeddingModelRequest;
import com.gacfox.meowclaw.service.EmbeddingModelService;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/embedding-model")
public class EmbeddingModelController {
    private final EmbeddingModelService embeddingModelService;

    @Autowired
    public EmbeddingModelController(EmbeddingModelService embeddingModelService) {
        this.embeddingModelService = embeddingModelService;
    }

    @GetMapping
    public ApiResult<List<EmbeddingModelDTO>> list() {
        return ApiResult.success(embeddingModelService.list());
    }

    @PostMapping
    public ApiResult<EmbeddingModelDTO> create(@RequestBody @Valid CreateEmbeddingModelRequest req) {
        return ApiResult.success(embeddingModelService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<EmbeddingModelDTO> update(@PathVariable Long id,
                                                   @RequestBody @Valid UpdateEmbeddingModelRequest req) {
        return ApiResult.success(embeddingModelService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        embeddingModelService.delete(id);
        return ApiResult.success();
    }

    @PostMapping("/test")
    public ApiResult<EmbeddingTestResultDTO> test(@RequestBody @Valid EmbeddingModelTestRequest req) {
        return ApiResult.success(embeddingModelService.test(req));
    }
}
