package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 项目会话文件夹管理
 */
@Tag(name = "项目")
@RestController
@RequestMapping("/api/project")
public class ProjectController {
    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Operation(summary = "查询项目列表")
    @GetMapping
    public ApiResult<List<ProjectDTO>> list(@Parameter(description = "智能体 ID") @RequestParam Long agentId) {
        return ApiResult.success(projectService.listByAgent(agentId));
    }

    @Operation(summary = "创建项目")
    @PostMapping
    public ApiResult<ProjectDTO> create(@RequestBody Map<String, Object> body) {
        Long agentId = body.get("agentId") instanceof Number n ? n.longValue() : null;
        return ApiResult.success(projectService.create(agentId, (String) body.get("name")));
    }

    @Operation(summary = "更新项目名称")
    @PutMapping("/{id}")
    public ApiResult<ProjectDTO> update(@Parameter(description = "项目 ID") @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResult.success(projectService.update(id, body.get("name")));
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "项目 ID") @PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.success();
    }
}
