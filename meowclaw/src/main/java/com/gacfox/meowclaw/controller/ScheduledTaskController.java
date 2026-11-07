package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.ScheduledTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 定时任务配置管理
 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/api/scheduled-task")
public class ScheduledTaskController {
    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @Operation(summary = "查询定时任务列表")
    @GetMapping
    public ApiResult<List<ScheduledTaskDTO>> list() {
        return ApiResult.success(scheduledTaskService.list());
    }

    @Operation(summary = "创建定时任务")
    @PostMapping
    public ApiResult<ScheduledTaskDTO> create(@RequestBody @Valid CreateScheduledTaskRequest req) {
        return ApiResult.success(scheduledTaskService.create(req));
    }

    @Operation(summary = "更新定时任务")
    @PutMapping("/{id}")
    public ApiResult<ScheduledTaskDTO> update(@Parameter(description = "定时任务 ID") @PathVariable Long id, @RequestBody @Valid UpdateScheduledTaskRequest req) {
        return ApiResult.success(scheduledTaskService.update(id, req));
    }

    @Operation(summary = "删除定时任务")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "定时任务 ID") @PathVariable Long id) {
        scheduledTaskService.delete(id);
        return ApiResult.success();
    }

    @Operation(summary = "切换定时任务启用状态")
    @PutMapping("/{id}/toggle")
    public ApiResult<ScheduledTaskDTO> toggleEnabled(@Parameter(description = "定时任务 ID") @PathVariable Long id) {
        return ApiResult.success(scheduledTaskService.toggleEnabled(id));
    }

    @Operation(summary = "立即执行定时任务")
    @PostMapping("/{id}/trigger")
    public ApiResult<?> triggerOnce(@Parameter(description = "定时任务 ID") @PathVariable Long id) {
        scheduledTaskService.triggerOnce(id);
        return ApiResult.success();
    }

    @Operation(summary = "查询定时任务执行记录")
    @GetMapping("/{id}/execution")
    public ApiResult<List<ScheduledTaskExecutionDTO>> listExecutions(@Parameter(description = "定时任务 ID") @PathVariable Long id) {
        return ApiResult.success(scheduledTaskService.listExecutions(id));
    }
}
