package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.PageDto;
import com.gacfox.meowclaw.dto.ScheduledTaskDto;
import com.gacfox.meowclaw.service.ScheduledTaskService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/scheduled-tasks")
public class ScheduledTaskController {
    private final ScheduledTaskService scheduledTaskService;

    public ScheduledTaskController(ScheduledTaskService scheduledTaskService) {
        this.scheduledTaskService = scheduledTaskService;
    }

    @GetMapping
    public ApiResponse<PageDto<ScheduledTaskDto>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(scheduledTaskService.findAll(page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<ScheduledTaskDto> getById(@PathVariable Long id) {
        return ApiResponse.success(scheduledTaskService.findById(id));
    }

    @PostMapping
    public ApiResponse<ScheduledTaskDto> create(@Valid @RequestBody ScheduledTaskDto dto) {
        return ApiResponse.success(scheduledTaskService.create(dto));
    }

    @PutMapping("/{id}")
    public ApiResponse<ScheduledTaskDto> update(@PathVariable Long id, @Valid @RequestBody ScheduledTaskDto dto) {
        return ApiResponse.success(scheduledTaskService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        scheduledTaskService.delete(id);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/toggle")
    public ApiResponse<ScheduledTaskDto> toggleEnabled(@PathVariable Long id) {
        return ApiResponse.success(scheduledTaskService.toggleEnabled(id));
    }

    @PostMapping("/{id}/trigger")
    public ApiResponse<Void> trigger(@PathVariable Long id) {
        scheduledTaskService.trigger(id);
        return ApiResponse.success();
    }

    @GetMapping("/next-execution")
    public ApiResponse<String> getNextExecution(@RequestParam String cronExpression) {
        return ApiResponse.success(scheduledTaskService.getNextExecutionTime(cronExpression));
    }
}
