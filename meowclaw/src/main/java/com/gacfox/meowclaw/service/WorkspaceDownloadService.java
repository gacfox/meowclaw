package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.DownloadTicketDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 短期一次性下载票据。票据只在内存中保存，下载 URL 不需要也不接受 JWT 作为替代鉴权。
 */
@Service
public class WorkspaceDownloadService {
    private static final long TICKET_TTL_MILLIS = 5 * 60 * 1000L;

    private final WorkspaceService workspaceService;
    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentHashMap<String, DownloadTicket> tickets = new ConcurrentHashMap<>();

    @Autowired
    public WorkspaceDownloadService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    public DownloadTicketDTO prepare(Long agentId, List<String> paths) throws IOException {
        if (paths == null || paths.isEmpty() || paths.stream().anyMatch(path -> path == null)) {
            throw new IllegalArgumentException("路径不能为空");
        }
        Path root = workspaceService.resolveRoot(agentId);
        List<DownloadSource> sources = new ArrayList<>();
        for (String path : paths) {
            Path source = workspaceService.resolveTree(root, path);
            String relativePath = workspaceService.relativePath(root, source);
            sources.add(new DownloadSource(relativePath, source));
        }

        String filename = downloadFilename(root, sources);
        String ticket = newTicket();
        long now = System.currentTimeMillis();
        tickets.put(ticket, new DownloadTicket(root, sources, filename, now + TICKET_TTL_MILLIS));
        cleanupExpired();
        return new DownloadTicketDTO(ticket, filename);
    }

    /** 原子移除票据，因此即使下载过程中失败，票据也不能再次使用。 */
    public WorkspaceDownload open(String ticket) {
        DownloadTicket downloadTicket = ticket == null ? null : tickets.remove(ticket);
        if (downloadTicket == null || downloadTicket.expiresAt() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("下载票据无效或已过期");
        }
        try {
            List<DownloadSource> validated = validateSources(downloadTicket);
            boolean singleFile = validated.size() == 1
                    && Files.isRegularFile(validated.get(0).path());
            Long contentLength = singleFile ? Files.size(validated.get(0).path()) : null;
            StreamingResponseBody body = singleFile
                    ? outputStream -> Files.copy(validated.get(0).path(), outputStream)
                    : outputStream -> writeZip(downloadTicket.root(), validated, outputStream);
            return new WorkspaceDownload(downloadTicket.filename(), contentLength, body);
        } catch (IOException e) {
            throw new IllegalArgumentException("下载来源已变化或不可读", e);
        }
    }

    @Scheduled(initialDelay = 60_000L, fixedDelay = 60_000L)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private List<DownloadSource> validateSources(DownloadTicket ticket) throws IOException {
        List<DownloadSource> validated = new ArrayList<>();
        for (DownloadSource source : ticket.sources()) {
            Path realPath = workspaceService.resolveTree(ticket.root(), source.relativePath());
            validated.add(new DownloadSource(source.relativePath(), realPath));
        }
        return validated;
    }

    private void writeZip(Path root, List<DownloadSource> sources, OutputStream outputStream) throws IOException {
        Set<String> writtenEntries = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            for (DownloadSource source : sources) {
                String prefix = source.relativePath().isEmpty()
                        ? root.getFileName().toString() : source.relativePath();
                addZipEntry(root, source.path(), prefix, zip, writtenEntries);
            }
            zip.finish();
        }
    }

    private void addZipEntry(Path root, Path source, String entryName,
                             ZipOutputStream zip, Set<String> writtenEntries) throws IOException {
        if (Files.isSymbolicLink(source)) {
            throw new IOException("不支持下载符号链接");
        }
        Path realSource = source.toRealPath();
        if (!realSource.startsWith(root)) {
            throw new IOException("下载路径越出工作区");
        }
        String safeName = safeEntryName(entryName);
        if (Files.isDirectory(realSource)) {
            String directoryEntry = safeName + "/";
            if (writtenEntries.add(directoryEntry)) {
                zip.putNextEntry(new ZipEntry(directoryEntry));
                zip.closeEntry();
            }
            try (var children = Files.list(realSource)) {
                for (Path child : children.toList()) {
                    addZipEntry(root, child, safeName + "/" + child.getFileName(), zip, writtenEntries);
                }
            }
        } else if (Files.isRegularFile(realSource)) {
            if (writtenEntries.add(safeName)) {
                zip.putNextEntry(new ZipEntry(safeName));
                Files.copy(realSource, zip);
                zip.closeEntry();
            }
        } else {
            throw new IOException("不支持的文件类型");
        }
    }

    private String safeEntryName(String entryName) {
        String normalized = entryName;
        if (normalized.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("ZIP 条目名称包含不支持的路径字符");
        }
        Path path = Paths.get(normalized);
        if (normalized.isBlank() || normalized.startsWith("/") || path.isAbsolute()
                || normalized.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("非法 ZIP 条目路径");
        }
        for (Path segment : path) {
            String value = segment.toString();
            if (value.equals("..") || value.equals(".") || value.isBlank()) {
                throw new IllegalArgumentException("非法 ZIP 条目路径");
            }
        }
        return normalized;
    }

    private String downloadFilename(Path root, List<DownloadSource> sources) {
        if (sources.size() == 1) {
            DownloadSource source = sources.get(0);
            String name = source.path().getFileName() == null
                    ? root.getFileName().toString() : source.path().getFileName().toString();
            return Files.isRegularFile(source.path()) ? name : name + ".zip";
        }
        return "workspace-download.zip";
    }

    private String newTicket() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record WorkspaceDownload(String filename, Long contentLength, StreamingResponseBody body) {
    }

    private record DownloadSource(String relativePath, Path path) {
    }

    private record DownloadTicket(Path root, List<DownloadSource> sources, String filename, long expiresAt) {
    }
}
