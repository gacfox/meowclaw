package com.gacfox.meowclaw.controller;

import com.gacfox.meowclaw.dto.*;
import com.gacfox.meowclaw.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 工作区管理
 */
@Tag(name = "工作区")
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Operation(summary = "查询工作区目录")
    @GetMapping
    public ApiResult<List<FileEntryDTO>> list(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                              @Parameter(description = "目录相对路径，不传则查询根目录") @RequestParam(required = false) String path) throws IOException {
        return ApiResult.success(workspaceService.list(agentId, path));
    }

    @Operation(summary = "读取文件内容")
    @GetMapping("/file")
    public ApiResult<FileContentDTO> read(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                          @Parameter(description = "文件相对路径") @RequestParam String path) throws IOException {
        return ApiResult.success(workspaceService.read(agentId, path));
    }

    @Operation(summary = "下载工作区文件")
    @GetMapping("/download")
    public ResponseEntity<Resource> download(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                             @Parameter(description = "文件相对路径") @RequestParam String path) throws IOException {
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

    @Operation(summary = "保存文本文件")
    @PutMapping("/file")
    public ApiResult<?> save(@RequestBody @Valid SaveFileRequest req) throws IOException {
        workspaceService.saveText(req.getAgentId(), req.getPath(), req.getContent());
        return ApiResult.success();
    }

    @Operation(summary = "移动文件或目录")
    @PostMapping("/move")
    public ApiResult<?> move(@RequestBody @Valid MoveRequest req) throws IOException {
        workspaceService.move(req.getAgentId(), req.getFromPath(), req.getToPath());
        return ApiResult.success();
    }

    @Operation(summary = "创建文件或目录")
    @PostMapping("/create")
    public ApiResult<?> create(@RequestBody @Valid CreateEntryRequest req) throws IOException {
        workspaceService.create(req.getAgentId(), req.getPath(), req.getType());
        return ApiResult.success();
    }

    @Operation(summary = "上传文件到工作区")
    @PostMapping("/upload")
    public ApiResult<FileEntryDTO> upload(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                          @Parameter(description = "目标目录相对路径，不传则上传到根目录") @RequestParam(required = false) String path,
                                          @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResult.success(workspaceService.upload(agentId, path, file));
    }

    @Operation(summary = "删除文件或目录")
    @DeleteMapping
    public ApiResult<?> delete(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                               @Parameter(description = "文件或目录相对路径") @RequestParam String path) throws IOException {
        workspaceService.delete(agentId, path);
        return ApiResult.success();
    }
}
