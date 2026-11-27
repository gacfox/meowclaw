package com.gacfox.meowclaw.service.workspace;

import com.gacfox.meowclaw.dto.workspace.UploadCompleteDTO;
import com.gacfox.meowclaw.dto.workspace.UploadSessionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 原始字节分块上传。块和合并文件始终位于工作区之外，只有完整校验成功后才落入目标目录。
 */
@Slf4j
@Service
public class WorkspaceUploadService {
    public static final int MAX_CHUNK_SIZE = 8 * 1024 * 1024;
    private static final long SESSION_TTL_MILLIS = 24 * 60 * 60 * 1000L;

    private final WorkspaceService workspaceService;
    private final Path tempRoot;
    private final ConcurrentHashMap<String, UploadSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public WorkspaceUploadService(WorkspaceService workspaceService,
                                  @Value("${meowclaw.data-dir}") String dataDir) {
        this.workspaceService = workspaceService;
        this.tempRoot = Paths.get(dataDir, "tmp", "workspace-upload").toAbsolutePath().normalize();
    }

    public UploadSessionDTO create(Long agentId, String targetDir, String fileName,
                                   Long fileSize, Integer chunkSize, Integer totalChunks) throws IOException {
        Path root = workspaceService.resolveRoot(agentId);
        workspaceService.resolveDirectory(root, targetDir);
        String validFileName = workspaceService.validateFileName(fileName);
        validateMetadata(fileSize, chunkSize, totalChunks);
        cleanupExpired();

        Files.createDirectories(tempRoot);
        String uploadId = UUID.randomUUID().toString();
        Path sessionDir = tempRoot.resolve(uploadId).normalize();
        if (!sessionDir.startsWith(tempRoot)) {
            throw new IllegalArgumentException("非法上传会话目录");
        }
        if (sessionDir.startsWith(root)) {
            throw new IllegalStateException("上传临时目录不能位于工作区内");
        }
        Files.createDirectory(sessionDir);
        long now = System.currentTimeMillis();
        UploadSession session = new UploadSession(uploadId, agentId, targetDir, validFileName,
                fileSize, chunkSize, totalChunks, sessionDir, now);
        sessions.put(uploadId, session);
        return toDTO(session);
    }

    public UploadSessionDTO get(String uploadId) throws IOException {
        UploadSession session = getActive(uploadId);
        synchronized (session) {
            session.lastAccess = System.currentTimeMillis();
            return toDTO(session);
        }
    }

    public UploadSessionDTO putChunk(String uploadId, int index, byte[] body) throws IOException {
        UploadSession session = getActive(uploadId);
        synchronized (session) {
            if (index < 0 || index >= session.totalChunks) {
                throw new IllegalArgumentException("块序号无效");
            }
            if (body == null) {
                throw new IllegalArgumentException("块内容不能为空");
            }
            long expectedSize = expectedChunkSize(session, index);
            if (body.length != expectedSize) {
                throw new IllegalArgumentException("块大小不符合上传会话元数据");
            }

            Path existing = session.chunks.get(index);
            if (existing != null) {
                if (Files.size(existing) != body.length
                        || !Arrays.equals(Files.readAllBytes(existing), body)) {
                    throw new IllegalArgumentException("相同块序号的内容不一致");
                }
                session.lastAccess = System.currentTimeMillis();
                return toDTO(session);
            }

            Path chunk = session.directory.resolve("chunk-" + index + ".bin");
            Path temporary = session.directory.resolve("chunk-" + index + "-" + UUID.randomUUID() + ".tmp");
            try {
                Files.write(temporary, body, StandardOpenOption.CREATE_NEW);
                Files.move(temporary, chunk);
                session.chunks.put(index, chunk);
            } finally {
                Files.deleteIfExists(temporary);
            }
            session.lastAccess = System.currentTimeMillis();
            return toDTO(session);
        }
    }

    public UploadCompleteDTO complete(String uploadId, String conflict) throws IOException {
        UploadSession session = getActive(uploadId);
        synchronized (session) {
            if (sessions.get(uploadId) != session) {
                throw new IllegalArgumentException("上传会话已完成、取消或过期");
            }
            if (session.chunks.size() != session.totalChunks || !hasAllChunks(session)) {
                throw new IllegalArgumentException("仍有分块未上传");
            }

            Path merged = session.directory.resolve("merged-" + UUID.randomUUID() + ".tmp");
            try {
                mergeAndVerify(session, merged);
                UploadCompleteDTO result = workspaceService.storeUploadedFile(
                        session.agentId, session.targetDir, session.fileName, merged, conflict);
                sessions.remove(uploadId, session);
                cleanupSessionDirectory(session);
                return result;
            } finally {
                try {
                    Files.deleteIfExists(merged);
                } catch (IOException e) {
                    log.warn("清理上传合并临时文件失败：{}", merged, e);
                }
            }
        }
    }

    public void cancel(String uploadId) throws IOException {
        UploadSession session = sessions.remove(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }
        synchronized (session) {
            WorkspaceService.deleteRecursively(session.directory);
        }
    }

    @Scheduled(initialDelay = 60 * 60 * 1000L, fixedDelay = 60 * 60 * 1000L)
    public void cleanupExpired() {
        long cutoff = System.currentTimeMillis() - SESSION_TTL_MILLIS;
        for (UploadSession session : sessions.values()) {
            if (session.lastAccess < cutoff && sessions.remove(session.uploadId, session)) {
                synchronized (session) {
                    try {
                        WorkspaceService.deleteRecursively(session.directory);
                    } catch (IOException e) {
                        log.warn("清理过期上传会话失败：{}", session.uploadId, e);
                    }
                }
            }
        }
    }

    private void cleanupSessionDirectory(UploadSession session) {
        try {
            WorkspaceService.deleteRecursively(session.directory);
        } catch (IOException e) {
            // 文件已经落入工作区，临时目录清理失败不能把成功上传改报为失败。
            log.warn("清理已完成上传会话失败：{}", session.uploadId, e);
        }
    }

    private UploadSession getActive(String uploadId) throws IOException {
        UploadSession session = uploadId == null ? null : sessions.get(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }
        if (session.lastAccess < System.currentTimeMillis() - SESSION_TTL_MILLIS) {
            if (sessions.remove(uploadId, session)) {
                WorkspaceService.deleteRecursively(session.directory);
            }
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }
        return session;
    }

    private void mergeAndVerify(UploadSession session, Path merged) throws IOException {
        long total = 0;
        try (OutputStream output = new BufferedOutputStream(
                Files.newOutputStream(merged, StandardOpenOption.CREATE_NEW))) {
            for (int index = 0; index < session.totalChunks; index++) {
                Path chunk = session.chunks.get(index);
                if (chunk == null || Files.size(chunk) != expectedChunkSize(session, index)) {
                    throw new IllegalArgumentException("分块缺失或大小已变化");
                }
                long copied = Files.copy(chunk, output);
                try {
                    total = Math.addExact(total, copied);
                } catch (ArithmeticException e) {
                    throw new IllegalArgumentException("文件大小溢出", e);
                }
            }
        }
        if (total != session.fileSize || Files.size(merged) != session.fileSize) {
            throw new IllegalArgumentException("合并后的文件大小与元数据不一致");
        }
    }

    private boolean hasAllChunks(UploadSession session) {
        for (int index = 0; index < session.totalChunks; index++) {
            if (!session.chunks.containsKey(index)) {
                return false;
            }
        }
        return true;
    }

    private long expectedChunkSize(UploadSession session, int index) {
        if (index == session.totalChunks - 1) {
            return session.fileSize - (long) index * session.chunkSize;
        }
        return session.chunkSize;
    }

    private void validateMetadata(Long fileSize, Integer chunkSize, Integer totalChunks) {
        if (fileSize == null || fileSize < 0) {
            throw new IllegalArgumentException("文件大小必须是非负数");
        }
        if (chunkSize == null || chunkSize <= 0 || chunkSize > MAX_CHUNK_SIZE) {
            throw new IllegalArgumentException("块大小必须在 1 字节到 8MiB 之间");
        }
        if (totalChunks == null || totalChunks < 0) {
            throw new IllegalArgumentException("总块数必须是非负数");
        }
        long expectedChunks = fileSize / chunkSize + (fileSize % chunkSize == 0 ? 0 : 1);
        if (expectedChunks != totalChunks.longValue()) {
            throw new IllegalArgumentException("总块数与文件大小、块大小不匹配");
        }
        if (fileSize == 0 && totalChunks != 0) {
            throw new IllegalArgumentException("零字节文件的总块数必须为 0");
        }
    }

    private UploadSessionDTO toDTO(UploadSession session) {
        List<Integer> received = new ArrayList<>(session.chunks.keySet());
        Collections.sort(received);
        return new UploadSessionDTO(session.uploadId, session.fileName, session.fileSize,
                session.chunkSize, session.totalChunks, List.copyOf(received));
    }

    private static final class UploadSession {
        private final String uploadId;
        private final Long agentId;
        private final String targetDir;
        private final String fileName;
        private final long fileSize;
        private final int chunkSize;
        private final int totalChunks;
        private final Path directory;
        private final Map<Integer, Path> chunks = new HashMap<>();
        private volatile long lastAccess;

        private UploadSession(String uploadId, Long agentId, String targetDir, String fileName,
                              long fileSize, int chunkSize, int totalChunks, Path directory, long now) {
            this.uploadId = uploadId;
            this.agentId = agentId;
            this.targetDir = targetDir;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.chunkSize = chunkSize;
            this.totalChunks = totalChunks;
            this.directory = directory;
            this.lastAccess = now;
        }
    }
}
