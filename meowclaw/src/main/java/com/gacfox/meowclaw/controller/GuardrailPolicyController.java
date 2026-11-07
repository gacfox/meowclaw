package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.SaveGuardrailPolicyRequest;
import com.gacfox.meowclaw.service.GuardrailPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 安全围栏策略管理
 */
@Tag(name = "安全策略")
@RestController
@RequestMapping("/api/guardrail/policy")
public class GuardrailPolicyController {
    private final GuardrailPolicyService guardrailPolicyService;

    @Autowired
    public GuardrailPolicyController(GuardrailPolicyService guardrailPolicyService) {
        this.guardrailPolicyService = guardrailPolicyService;
    }

    @Operation(summary = "查询安全策略列表")
    @GetMapping
    public ApiResult<List<GuardrailPolicyDTO>> list() {
        return ApiResult.success(guardrailPolicyService.list());
    }

    @Operation(summary = "创建安全策略")
    @PostMapping
    public ApiResult<GuardrailPolicyDTO> create(@RequestBody @Valid SaveGuardrailPolicyRequest req) {
        return ApiResult.success(guardrailPolicyService.create(req));
    }

    @Operation(summary = "更新安全策略")
    @PutMapping("/{id}")
    public ApiResult<GuardrailPolicyDTO> update(@Parameter(description = "安全策略 ID") @PathVariable Long id,
                                                @RequestBody @Valid SaveGuardrailPolicyRequest req) {
        return ApiResult.success(guardrailPolicyService.update(id, req));
    }

    @Operation(summary = "删除安全策略")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "安全策略 ID") @PathVariable Long id) {
        guardrailPolicyService.delete(id);
        return ApiResult.success();
    }
}
