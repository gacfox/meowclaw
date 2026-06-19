package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateScheduledTaskRequest;
import com.gacfox.meowclaw.dto.ScheduledTaskDTO;
import com.gacfox.meowclaw.dto.ScheduledTaskExecutionDTO;
import com.gacfox.meowclaw.dto.UpdateScheduledTaskRequest;
import com.gacfox.meowclaw.service.ScheduledTaskService;
import com.gacfox.proarc.common.model.ApiResult;
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
@RequestMapping("/api/scheduled-task")
public class ScheduledTaskController {
    private final ScheduledTaskService scheduledTaskService;

    @Autowired
    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @GetMapping
    public ApiResult<List<ScheduledTaskDTO>> list() {
        return ApiResult.success(scheduledTaskService.list());
    }

    @PostMapping
    public ApiResult<ScheduledTaskDTO> create(@RequestBody @Valid CreateScheduledTaskRequest req) {
        return ApiResult.success(scheduledTaskService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResult<ScheduledTaskDTO> update(@PathVariable Long id, @RequestBody @Valid UpdateScheduledTaskRequest req) {
        return ApiResult.success(scheduledTaskService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        scheduledTaskService.delete(id);
        return ApiResult.success();
    }

    @PutMapping("/{id}/toggle")
    public ApiResult<ScheduledTaskDTO> toggleEnabled(@PathVariable Long id) {
        return ApiResult.success(scheduledTaskService.toggleEnabled(id));
    }

    @PostMapping("/{id}/trigger")
    public ApiResult<?> triggerOnce(@PathVariable Long id) {
        scheduledTaskService.triggerOnce(id);
        return ApiResult.success();
    }

    @GetMapping("/{id}/execution")
    public ApiResult<List<ScheduledTaskExecutionDTO>> listExecutions(@PathVariable Long id) {
        return ApiResult.success(scheduledTaskService.listExecutions(id));
    }
}
