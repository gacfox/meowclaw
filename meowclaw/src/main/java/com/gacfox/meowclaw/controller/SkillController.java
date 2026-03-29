package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.SkillDto;
import com.gacfox.meowclaw.dto.SkillUpdateDto;
import com.gacfox.meowclaw.service.SkillService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ApiResponse<List<SkillDto>> list() {
        return ApiResponse.success(skillService.list());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillDto> upload(@RequestParam @NotBlank String name,
                                        @RequestParam(required = false) String description,
                                        @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(skillService.upload(name, description, file));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<SkillDto> update(@PathVariable Long id, @Valid @RequestBody SkillUpdateDto dto) {
        return ApiResponse.success(skillService.update(id, dto.getName(), dto.getDescription()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Path packagePath = skillService.getSkillPackagePath(id);
        Resource resource = new FileSystemResource(packagePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + packagePath.getFileName().toString() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    public ApiResponse<String> preview(@PathVariable Long id) {
        return ApiResponse.success(skillService.readSkillPrompt(id));
    }
}
