package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateMemoryRequest;
import com.gacfox.meowclaw.dto.MemoryEntityDTO;
import com.gacfox.meowclaw.dto.MemoryGraphDTO;
import com.gacfox.meowclaw.dto.MemoryNodeDTO;
import com.gacfox.meowclaw.dto.UpdateMemoryRequest;
import com.gacfox.meowclaw.service.MemoryService;
import com.gacfox.proarc.common.model.ApiResult;
import com.gacfox.proarc.common.model.Pagination;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/memory")
public class MemoryController {
    private final MemoryService memoryService;

    @Autowired
    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public ApiResult<Pagination<MemoryNodeDTO>> list(@RequestParam Long agentId,
                                                     @RequestParam(required = false) String type,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Long entityId,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(memoryService.list(agentId, type, keyword, entityId, page, size));
    }

    @GetMapping("/recall-preview")
    public ApiResult<List<MemoryNodeDTO>> recallPreview(@RequestParam Long agentId,
                                                        @RequestParam String query,
                                                        @RequestParam(required = false) Integer limit) {
        return ApiResult.success(memoryService.recallPreview(agentId, query, limit));
    }

    @GetMapping("/entities")
    public ApiResult<List<MemoryEntityDTO>> listEntities(@RequestParam Long agentId) {
        return ApiResult.success(memoryService.listEntities(agentId));
    }

    @GetMapping("/graph")
    public ApiResult<MemoryGraphDTO> graph(@RequestParam Long agentId) {
        return ApiResult.success(memoryService.graph(agentId));
    }

    @PostMapping
    public ApiResult<MemoryNodeDTO> create(@RequestBody @Valid CreateMemoryRequest req) {
        return ApiResult.success(memoryService.createManual(req.getAgentId(), req.getType(), req.getContent()));
    }

    @PutMapping("/{id}")
    public ApiResult<MemoryNodeDTO> update(@PathVariable Long id, @RequestBody UpdateMemoryRequest req) {
        return ApiResult.success(memoryService.updateManual(id, req.getType(), req.getContent()));
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        memoryService.deleteManual(id);
        return ApiResult.success();
    }
}
