package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.ApiResponse;
import com.gacfox.meowclaw.dto.WorkspaceEntryDto;
import com.gacfox.meowclaw.dto.WorkspacePreviewDto;
import com.gacfox.meowclaw.service.WorkspaceService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping("/agent/{agentId}/list")
    public ApiResponse<List<WorkspaceEntryDto>> list(@PathVariable Long agentId,
                                                     @RequestParam(required = false) String path) {
        return ApiResponse.success(workspaceService.listEntries(agentId, path));
    }

    @GetMapping("/agent/{agentId}/preview")
    public ApiResponse<WorkspacePreviewDto> preview(@PathVariable Long agentId,
                                                    @RequestParam String path) {
        return ApiResponse.success(workspaceService.preview(agentId, path));
    }

    @GetMapping("/agent/{agentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long agentId,
                                             @RequestParam String path) {
        Path target = workspaceService.getDownloadPath(agentId, path);
        Resource resource = new FileSystemResource(target);
        String filename = target.getFileName().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @DeleteMapping("/agent/{agentId}")
    public ApiResponse<Void> delete(@PathVariable Long agentId,
                                    @RequestParam String path) {
        workspaceService.delete(agentId, path);
        return ApiResponse.success(null);
    }

    @PostMapping("/agent/{agentId}/move")
    public ApiResponse<Void> move(@PathVariable Long agentId,
                                  @RequestBody Map<String, String> body) {
        String from = body.get("from");
        String to = body.get("to");
        workspaceService.move(agentId, from, to);
        return ApiResponse.success(null);
    }
}
