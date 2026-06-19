package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.SkillInstallRequest;
import com.gacfox.meowclaw.dto.SkillInstallResultDTO;
import com.gacfox.meowclaw.dto.SkillPackageDTO;
import com.gacfox.meowclaw.service.SkillService;
import com.gacfox.proarc.common.model.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/skill")
public class SkillController {
    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ApiResult<List<SkillPackageDTO>> list() {
        return ApiResult.success(skillService.list());
    }

    @PostMapping
    public ApiResult<SkillPackageDTO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResult.success(skillService.upload(file));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ApiResult.success();
    }

    @PostMapping("/{id}/install")
    public ApiResult<SkillInstallResultDTO> install(@PathVariable Long id,
                                                    @RequestBody @Valid SkillInstallRequest req) throws IOException {
        return ApiResult.success(skillService.install(id, req));
    }
}
