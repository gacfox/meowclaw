package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 长期记忆管理
 */
@Tag(name = "长期记忆")
@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private final MemoryService memoryService;

    @Autowired
    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Operation(summary = "查询长期记忆列表")
    @GetMapping
    public ApiResult<Pagination<MemoryNodeDTO>> list(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                                     @Parameter(description = "记忆类型") @RequestParam(required = false) String type,
                                                     @Parameter(description = "记忆内容搜索关键词") @RequestParam(required = false) String keyword,
                                                     @Parameter(description = "记忆实体 ID") @RequestParam(required = false) Long entityId,
                                                     @Parameter(description = "页码，从 1 开始") @RequestParam(defaultValue = "1") int page,
                                                     @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(memoryService.list(agentId, type, keyword, entityId, page, size));
    }

    @Operation(summary = "预览记忆召回结果")
    @GetMapping("/recall-preview")
    public ApiResult<List<MemoryNodeDTO>> recallPreview(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                                        @Parameter(description = "召回查询内容") @RequestParam String query,
                                                        @Parameter(description = "最多返回的记忆条数") @RequestParam(required = false) Integer limit) {
        return ApiResult.success(memoryService.recallPreview(agentId, query, limit));
    }

    @Operation(summary = "查询记忆实体列表")
    @GetMapping("/entities")
    public ApiResult<List<MemoryEntityDTO>> listEntities(@Parameter(description = "智能体 ID") @RequestParam Long agentId) {
        return ApiResult.success(memoryService.listEntities(agentId));
    }

    @Operation(summary = "查询记忆图谱")
    @GetMapping("/graph")
    public ApiResult<MemoryGraphDTO> graph(@Parameter(description = "智能体 ID") @RequestParam Long agentId) {
        return ApiResult.success(memoryService.graph(agentId));
    }

    @Operation(summary = "创建长期记忆")
    @PostMapping
    public ApiResult<MemoryNodeDTO> create(@RequestBody @Valid CreateMemoryRequest req) {
        return ApiResult.success(memoryService.createManual(req.getAgentId(), req.getType(), req.getContent()));
    }

    @Operation(summary = "更新长期记忆")
    @PutMapping("/{id}")
    public ApiResult<MemoryNodeDTO> update(@Parameter(description = "记忆 ID") @PathVariable Long id, @RequestBody UpdateMemoryRequest req) {
        return ApiResult.success(memoryService.updateManual(id, req.getType(), req.getContent()));
    }

    @Operation(summary = "删除长期记忆")
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@Parameter(description = "记忆 ID") @PathVariable Long id) {
        memoryService.deleteManual(id);
        return ApiResult.success();
    }
}
