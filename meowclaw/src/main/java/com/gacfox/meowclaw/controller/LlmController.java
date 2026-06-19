package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateLlmRequest;
import com.gacfox.meowclaw.dto.LlmDTO;
import com.gacfox.meowclaw.dto.UpdateLlmRequest;
import com.gacfox.meowclaw.service.LlmService;
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
@RequestMapping("/api/llm")
public class LlmController {
    private final LlmService llmService;

    @Autowired
    public LlmController(LlmService llmService) {
        this.llmService = llmService;
    }

    @GetMapping
    public ApiResult<List<LlmDTO>> list() {
        return ApiResult.success(llmService.list());
    }

    @PostMapping
    public ApiResult<LlmDTO> create(@RequestBody @Valid CreateLlmRequest req) {
        return ApiResult.success(llmService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<LlmDTO> update(@PathVariable Long id, @RequestBody @Valid UpdateLlmRequest req) {
        return ApiResult.success(llmService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        llmService.delete(id);
        return ApiResult.success();
    }
}
