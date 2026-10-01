package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.SaveGuardrailPolicyRequest;
import com.gacfox.meowclaw.service.GuardrailPolicyService;
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
@RequestMapping("/api/guardrail/policy")
public class GuardrailPolicyController {
    private final GuardrailPolicyService guardrailPolicyService;

    @Autowired
    public GuardrailPolicyController(GuardrailPolicyService guardrailPolicyService) {
        this.guardrailPolicyService = guardrailPolicyService;
    }

    @GetMapping
    public ApiResult<List<GuardrailPolicyDTO>> list() {
        return ApiResult.success(guardrailPolicyService.list());
    }

    @PostMapping
    public ApiResult<GuardrailPolicyDTO> create(@RequestBody @Valid SaveGuardrailPolicyRequest req) {
        return ApiResult.success(guardrailPolicyService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<GuardrailPolicyDTO> update(@PathVariable Long id,
                                                @RequestBody @Valid SaveGuardrailPolicyRequest req) {
        return ApiResult.success(guardrailPolicyService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        guardrailPolicyService.delete(id);
        return ApiResult.success();
    }
}
