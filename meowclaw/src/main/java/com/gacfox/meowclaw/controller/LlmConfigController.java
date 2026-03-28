package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.LlmConfigDto;
import com.gacfox.meowclaw.service.LlmConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/llms")
public class LlmConfigController {
    private final LlmConfigService llmConfigService;

    public LlmConfigController(LlmConfigService llmConfigService) {
        this.llmConfigService = llmConfigService;
    }

    @GetMapping
    public ApiResponse<List<LlmConfigDto>> list() {
        return ApiResponse.success(llmConfigService.findAll());
    }

    @GetMapping("/{id}")
    public ApiResponse<LlmConfigDto> getById(@PathVariable Long id) {
        return ApiResponse.success(llmConfigService.findById(id));
    }

    @PostMapping
    public ApiResponse<LlmConfigDto> create(@Valid @RequestBody LlmConfigDto dto) {
        return ApiResponse.success(llmConfigService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<LlmConfigDto> update(@PathVariable Long id, @Valid @RequestBody LlmConfigDto dto) {
        return ApiResponse.success(llmConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        llmConfigService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/test")
    public ApiResponse<Boolean> testConnection(@Valid @RequestBody LlmConfigDto dto) {
        return ApiResponse.success(llmConfigService.testConnection(dto));
    }
}
