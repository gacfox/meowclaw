package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.CreateEntryRequest;
import com.gacfox.meowclaw.dto.FileContentDTO;
import com.gacfox.meowclaw.dto.FileEntryDTO;
import com.gacfox.meowclaw.dto.MoveRequest;
import com.gacfox.meowclaw.dto.SaveFileRequest;
import com.gacfox.meowclaw.service.WorkspaceService;
import com.gacfox.meowclaw.dto.ApiResult;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResult<List<FileEntryDTO>> list(@RequestParam Long agentId,
                                           @RequestParam(required = false) String path) throws IOException {
        return ApiResult.success(workspaceService.list(agentId, path));
    }

    @GetMapping("/file")
    public ApiResult<FileContentDTO> read(@RequestParam Long agentId,
                                       @RequestParam String path) throws IOException {
        return ApiResult.success(workspaceService.read(agentId, path));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam Long agentId,
                                             @RequestParam String path) throws IOException {
        Path file = workspaceService.resolveFileForDownload(agentId, path);
        Resource resource = new FileSystemResource(file);
        String contentDisposition = ContentDisposition.attachment()
                .filename(file.getFileName().toString(), StandardCharsets.UTF_8)
                .build()
                .toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentLength(Files.size(file))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PutMapping("/file")
    public ApiResult<?> save(@RequestBody @Valid SaveFileRequest req) throws IOException {
        workspaceService.saveText(req.getAgentId(), req.getPath(), req.getContent());
        return ApiResult.success();
    }

    @PostMapping("/move")
    public ApiResult<?> move(@RequestBody @Valid MoveRequest req) throws IOException {
        workspaceService.move(req.getAgentId(), req.getFromPath(), req.getToPath());
        return ApiResult.success();
    }

    @PostMapping("/create")
    public ApiResult<?> create(@RequestBody @Valid CreateEntryRequest req) throws IOException {
        workspaceService.create(req.getAgentId(), req.getPath(), req.getType());
        return ApiResult.success();
    }

    @PostMapping("/upload")
    public ApiResult<FileEntryDTO> upload(@RequestParam Long agentId,
                                       @RequestParam(required = false) String path,
                                       @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResult.success(workspaceService.upload(agentId, path, file));
    }

    @DeleteMapping
    public ApiResult<?> delete(@RequestParam Long agentId,
                               @RequestParam String path) throws IOException {
        workspaceService.delete(agentId, path);
        return ApiResult.success();
    }
}
