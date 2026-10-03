package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.ProjectDTO;
import com.gacfox.meowclaw.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/project")
public class ProjectController {
    private final ProjectService projectService;

    @Autowired
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public ApiResult<List<ProjectDTO>> list(@RequestParam Long agentId) {
        return ApiResult.success(projectService.listByAgent(agentId));
    }

    @PostMapping
    public ApiResult<ProjectDTO> create(@RequestBody Map<String, Object> body) {
        Long agentId = body.get("agentId") instanceof Number n ? n.longValue() : null;
        return ApiResult.success(projectService.create(agentId, (String) body.get("name")));
    }

    @PutMapping("/{id}")
    public ApiResult<ProjectDTO> update(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResult.success(projectService.update(id, body.get("name")));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResult.success();
    }
}
