package com.gacfox.meowclaw.controller.workspace;

import com.gacfox.meowclaw.dto.common.ApiResult;
import com.gacfox.meowclaw.dto.workspace.*;
import com.gacfox.meowclaw.service.workspace.WorkspaceDownloadService;
import com.gacfox.meowclaw.service.workspace.WorkspaceFilePreview;
import com.gacfox.meowclaw.service.workspace.WorkspaceService;
import com.gacfox.meowclaw.service.workspace.WorkspaceUploadService;
import com.gacfox.meowclaw.util.ClientDisconnectUtil;
import com.gacfox.meowclaw.service.workspace.WorkspaceDownload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 工作区管理
 */
@Tag(name = "工作区")
@Slf4j
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    private final WorkspaceDownloadService downloadService;
    private final WorkspaceUploadService uploadService;

    @Autowired
    public WorkspaceController(WorkspaceService workspaceService,
                               WorkspaceDownloadService downloadService,
                               WorkspaceUploadService uploadService) {
        this.workspaceService = workspaceService;
        this.downloadService = downloadService;
        this.uploadService = uploadService;
    }

    @Operation(summary = "查询工作区目录")
    @GetMapping
    public ApiResult<List<FileEntryDTO>> list(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                              @Parameter(description = "目录相对路径，不传则查询根目录") @RequestParam(required = false) String path) throws IOException {
        return ApiResult.success(workspaceService.list(agentId, path));
    }

    @Operation(summary = "预览工作区文件")
    @GetMapping("/file")
    public ResponseEntity<StreamingResponseBody> read(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                                                      @Parameter(description = "文件相对路径") @RequestParam String path,
                                                      @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
        WorkspaceFilePreview preview = workspaceService.read(agentId, path);
        if (preview.getKind() != WorkspaceFilePreview.Kind.PDF || rangeHeader == null) {
            return previewResponse(preview, HttpStatus.OK)
                    .contentLength(preview.getSize())
                    .body(stream(preview.getFile(), 0, preview.getSize()));
        }

        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rangeHeader);
        } catch (IllegalArgumentException e) {
            return rangeNotSatisfiable(preview.getSize());
        }
        if (ranges.size() != 1) {
            return rangeNotSatisfiable(preview.getSize());
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(preview.getSize());
        long end = range.getRangeEnd(preview.getSize());
        if (start >= preview.getSize() || end < start) {
            return rangeNotSatisfiable(preview.getSize());
        }
        return previewResponse(preview, HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, preview.getSize()))
                .contentLength(end - start + 1)
                .body(stream(preview.getFile(), start, end - start + 1));
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

    @Operation(summary = "准备单文件或多选 ZIP 下载")
    @PostMapping("/download/prepare")
    public ApiResult<DownloadTicketDTO> prepareDownload(@RequestBody @Valid WorkspacePathsRequest req) throws IOException {
        return ApiResult.success(downloadService.prepare(req.getAgentId(), req.getPaths()));
    }

    @Operation(summary = "使用一次性票据下载文件或 ZIP")
    @GetMapping("/download-ticket/{ticket}")
    public ResponseEntity<StreamingResponseBody> downloadByTicket(@PathVariable String ticket) {
        WorkspaceDownload download = downloadService.open(ticket);
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.getFilename(), StandardCharsets.UTF_8)
                        .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        if (download.getContentLength() != null) {
            response.contentLength(download.getContentLength());
        }
        return response.body(download.getBody());
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

    @Operation(summary = "递归复制文件或目录到目标目录")
    @PostMapping("/copy")
    public ApiResult<BatchOperationResultDTO> copy(@RequestBody @Valid BatchTransferRequest req) throws IOException {
        return ApiResult.success(workspaceService.copy(req.getAgentId(), req.getSourcePaths(),
                req.getTargetDir(), req.getConflict()));
    }

    @Operation(summary = "批量移动文件或目录到目标目录")
    @PostMapping("/move-batch")
    public ApiResult<BatchOperationResultDTO> moveBatch(@RequestBody @Valid BatchTransferRequest req) throws IOException {
        return ApiResult.success(workspaceService.moveBatch(req.getAgentId(), req.getSourcePaths(),
                req.getTargetDir(), req.getConflict()));
    }

    @Operation(summary = "重命名单个文件或目录")
    @PostMapping("/rename")
    public ApiResult<BatchOperationResultDTO> rename(@RequestBody @Valid RenameEntryRequest req) throws IOException {
        return ApiResult.success(workspaceService.rename(req.getAgentId(), req.getPath(),
                req.getNewName(), req.getConflict()));
    }

    @Operation(summary = "批量递归删除")
    @PostMapping("/delete-batch")
    public ApiResult<BatchOperationResultDTO> deleteBatch(@RequestBody @Valid WorkspacePathsRequest req) throws IOException {
        return ApiResult.success(workspaceService.deleteBatch(req.getAgentId(), req.getPaths()));
    }

    @Operation(summary = "递归统计文件属性")
    @PostMapping("/properties")
    public ApiResult<WorkspacePropertiesDTO> properties(@RequestBody @Valid WorkspacePathsRequest req) throws IOException {
        return ApiResult.success(workspaceService.properties(req.getAgentId(), req.getPaths()));
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

    @Operation(summary = "创建原始字节分块上传会话")
    @PostMapping("/upload/session")
    public ApiResult<UploadSessionDTO> createUploadSession(@RequestBody @Valid CreateUploadSessionRequest req) throws IOException {
        return ApiResult.success(uploadService.create(req.getAgentId(), req.getTargetDir(), req.getFileName(),
                req.getFileSize(), req.getChunkSize(), req.getTotalChunks()));
    }

    @Operation(summary = "查询分块上传会话")
    @GetMapping("/upload/session/{uploadId}")
    public ApiResult<UploadSessionDTO> getUploadSession(@PathVariable String uploadId) throws IOException {
        return ApiResult.success(uploadService.get(uploadId));
    }

    @Operation(summary = "上传一个原始字节分块")
    @PutMapping(value = "/upload/session/{uploadId}/chunk/{index}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResult<UploadSessionDTO> uploadChunk(@PathVariable String uploadId, @PathVariable int index,
                                                   @RequestBody byte[] body) throws IOException {
        return ApiResult.success(uploadService.putChunk(uploadId, index, body));
    }

    @Operation(summary = "合并并完成分块上传")
    @PostMapping("/upload/session/{uploadId}/complete")
    public ApiResult<UploadCompleteDTO> completeUpload(@PathVariable String uploadId,
                                                       @RequestBody @Valid CompleteUploadRequest req) throws IOException {
        return ApiResult.success(uploadService.complete(uploadId, req.getConflict()));
    }

    @Operation(summary = "取消分块上传并清理临时文件")
    @DeleteMapping("/upload/session/{uploadId}")
    public ApiResult<?> cancelUpload(@PathVariable String uploadId) throws IOException {
        uploadService.cancel(uploadId);
        return ApiResult.success();
    }

    @Operation(summary = "删除文件或目录")
    @DeleteMapping
    public ApiResult<?> delete(@Parameter(description = "智能体 ID") @RequestParam Long agentId,
                               @Parameter(description = "文件或目录相对路径") @RequestParam String path) throws IOException {
        workspaceService.delete(agentId, path);
        return ApiResult.success();
    }

    private ResponseEntity.BodyBuilder previewResponse(WorkspaceFilePreview preview, HttpStatus status) {
        MediaType contentType = preview.getKind() == WorkspaceFilePreview.Kind.TEXT
                ? new MediaType("text", "plain", StandardCharsets.UTF_8)
                : MediaType.parseMediaType(preview.getMimeType());
        return ResponseEntity.status(status)
                .header("X-Workspace-Kind", preview.getKind().name())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(preview.getFile().getFileName().toString(), StandardCharsets.UTF_8)
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private,no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(contentType);
    }

    private ResponseEntity<StreamingResponseBody> rangeNotSatisfiable(long size) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                .build();
    }

    StreamingResponseBody stream(Path file, long start, long length) {
        return output -> {
            try (SeekableByteChannel channel = Files.newByteChannel(file);
                 InputStream input = Channels.newInputStream(channel)) {
                channel.position(start);
                byte[] buffer = new byte[8192];
                long remaining = length;
                while (remaining > 0) {
                    int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (read < 0) {
                        break;
                    }
                    output.write(buffer, 0, read);
                    remaining -= read;
                }
            } catch (IOException e) {
                if (!ClientDisconnectUtil.isClientDisconnect(e)) {
                    throw e;
                }
                log.info("客户端取消工作区文件流输出：{}", file.getFileName());
            }
        };
    }
}
