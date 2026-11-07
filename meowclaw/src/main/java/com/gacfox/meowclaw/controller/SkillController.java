package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.SkillInstallRequest;
import com.gacfox.meowclaw.dto.SkillInstallResultDTO;
import com.gacfox.meowclaw.dto.SkillPackageDTO;
import com.gacfox.meowclaw.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 智能体技能配置管理
 */
@Tag(name = "技能")
@RestController
@RequestMapping("/api/skill")
public class SkillController {
    private final SkillService skillService;

    @Autowired
    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @Operation(summary = "查询技能包列表")
    @GetMapping
    public ApiResult<List<SkillPackageDTO>> list() {
        return ApiResult.success(skillService.list());
    }

    @Operation(summary = "上传技能包")
    @PostMapping
    public ApiResult<SkillPackageDTO> upload(@Parameter(description = "技能包文件") @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResult.success(skillService.upload(file));
    }

    @Operation(summary = "删除技能包")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "技能包 ID") @PathVariable Long id) {
        skillService.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "安装技能包")
    @PostMapping("/{id}/install")
    public ApiResult<SkillInstallResultDTO> install(@Parameter(description = "技能包 ID") @PathVariable Long id,
                                                    @RequestBody @Valid SkillInstallRequest req) throws IOException {
        return ApiResult.success(skillService.install(id, req));
    }
}
