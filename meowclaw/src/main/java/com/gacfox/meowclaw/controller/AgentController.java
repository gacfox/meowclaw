package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.AgentDTO;
import com.gacfox.meowclaw.dto.AgentEnvironmentDTO;
import com.gacfox.meowclaw.dto.ApiResult;
import com.gacfox.meowclaw.dto.CreateAgentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentEnvironmentRequest;
import com.gacfox.meowclaw.dto.UpdateAgentRequest;
import com.gacfox.meowclaw.service.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 智能体配置管理
 */
@Tag(name = "智能体")
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final AgentService agentService;

    @Autowired
    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @Operation(summary = "查询智能体列表")
    @GetMapping
    public ApiResult<List<AgentDTO>> list() {
        return ApiResult.success(agentService.list());
    }

    @Operation(summary = "创建智能体")
    @PostMapping
    public ApiResult<AgentDTO> create(@RequestBody @Valid CreateAgentRequest req) {
        return ApiResult.success(agentService.create(req));
    }

    @Operation(summary = "更新智能体")
    @PutMapping("/{id}")
    public ApiResult<AgentDTO> update(@Parameter(description = "智能体 ID") @PathVariable Long id, @RequestBody @Valid UpdateAgentRequest req) {
        return ApiResult.success(agentService.update(id, req));
    }

    @Operation(summary = "查询智能体环境变量")
    @GetMapping("/{id}/environment-variables")
    public ApiResult<AgentEnvironmentDTO> getEnvironment(@Parameter(description = "智能体 ID") @PathVariable Long id) {
        return ApiResult.success(agentService.getEnvironment(id));
    }

    @Operation(summary = "更新智能体环境变量")
    @PutMapping("/{id}/environment-variables")
    public ApiResult<AgentEnvironmentDTO> updateEnvironment(
            @Parameter(description = "智能体 ID") @PathVariable Long id,
            @RequestBody @Valid UpdateAgentEnvironmentRequest req) {
        return ApiResult.success(agentService.updateEnvironment(id, req));
    }

    @Operation(summary = "上传智能体头像")
    @PostMapping("/{id}/avatar")
    public ApiResult<String> uploadAvatar(@Parameter(description = "智能体 ID") @PathVariable Long id,
                                          @Parameter(description = "头像图片文件") @RequestParam("file") MultipartFile file) throws Exception {
        return ApiResult.success("success", agentService.updateAvatar(id, file));
    }

    @Operation(summary = "删除智能体")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "智能体 ID") @PathVariable Long id) {
        agentService.delete(id);
        return ApiResult.success();
    }
}
