package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.CreateProjectRequest;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.dto.UpdateProjectRequest;
import com.gacfox.meowclaw.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public ApiResult<ProjectDTO> create(@RequestBody @Valid CreateProjectRequest req) {
        return ApiResult.success(projectService.create(req.getAgentId(), req.getName(), req.getPath()));
    }

    @Operation(summary = "更新项目名称和工作目录")
    @PutMapping("/{id}")
    public ApiResult<ProjectDTO> update(@Parameter(description = "项目 ID") @PathVariable Long id,
                                        @RequestBody UpdateProjectRequest req) {
        return ApiResult.success(projectService.update(id, req.getName(), req.getPath()));
    }

    @Operation(summary = "删除项目")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "项目 ID") @PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.success();
    }
}
