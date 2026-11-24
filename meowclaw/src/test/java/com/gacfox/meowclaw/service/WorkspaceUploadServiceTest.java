package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.UploadCompleteDTO;
import com.gacfox.meowclaw.dto.UploadSessionDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceUploadServiceTest {
    @TempDir
    Path tempDir;

    private Path workspace;
    private Path dataDir;
    private WorkspaceUploadService uploadService;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
        dataDir = Files.createDirectory(tempDir.resolve("data"));
        AgentRepository repository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setWorkspaceFolder(workspace.toString());
        when(repository.findById(1L)).thenReturn(Optional.of(agent));
        uploadService = new WorkspaceUploadService(new WorkspaceService(repository), dataDir.toString());
    }

    @Test
    void acceptsOutOfOrderAndDuplicateChunksThenMerges() throws IOException {
        UploadSessionDTO session = uploadService.create(1L, "", "data.bin", 8L, 3, 3);

        uploadService.putChunk(session.getUploadId(), 2, bytes("gh"));
        uploadService.putChunk(session.getUploadId(), 0, bytes("abc"));
        UploadSessionDTO duplicate = uploadService.putChunk(session.getUploadId(), 0, bytes("abc"));
        assertThat(duplicate.getReceivedChunks()).containsExactly(0, 2);
        assertThat(workspace.resolve("data.bin")).doesNotExist();
        uploadService.putChunk(session.getUploadId(), 1, bytes("def"));

        UploadCompleteDTO result = uploadService.complete(session.getUploadId(), "FAIL");

        assertThat(result.isSkipped()).isFalse();
        assertThat(result.getTargetPath()).isEqualTo("data.bin");
        assertThat(result.getEntry().getSize()).isEqualTo(8);
        assertThat(workspace.resolve("data.bin")).hasBinaryContent(bytes("abcdefgh"));
        assertThat(dataDir.resolve("tmp/workspace-upload").resolve(session.getUploadId())).doesNotExist();
    }

    @Test
    void rejectsMissingWrongSizedAndMismatchedDuplicateChunks() throws IOException {
        UploadSessionDTO session = uploadService.create(1L, "", "data.bin", 5L, 3, 2);
        uploadService.putChunk(session.getUploadId(), 0, bytes("abc"));

        assertThatThrownBy(() -> uploadService.complete(session.getUploadId(), "FAIL"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("未上传");
        assertThatThrownBy(() -> uploadService.putChunk(session.getUploadId(), 2, bytes("de")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("序号");
        assertThatThrownBy(() -> uploadService.putChunk(session.getUploadId(), 1, bytes("d")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("块大小");
        assertThatThrownBy(() -> uploadService.putChunk(session.getUploadId(), 0, bytes("xyz")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不一致");

        uploadService.cancel(session.getUploadId());
        assertThat(dataDir.resolve("tmp/workspace-upload").resolve(session.getUploadId())).doesNotExist();
        assertThatThrownBy(() -> uploadService.get(session.getUploadId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failConflictRetainsSessionForRetryAndSkipCleansUp() throws IOException {
        Files.writeString(workspace.resolve("file.txt"), "old");
        UploadSessionDTO session = uploadService.create(1L, "", "file.txt", 3L, 3, 1);
        uploadService.putChunk(session.getUploadId(), 0, bytes("new"));

        assertThatThrownBy(() -> uploadService.complete(session.getUploadId(), "FAIL"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("目标已存在");
        assertThat(uploadService.get(session.getUploadId()).getReceivedChunks()).containsExactly(0);

        UploadCompleteDTO overwritten = uploadService.complete(session.getUploadId(), "OVERWRITE");
        assertThat(overwritten.getEntry().getPath()).isEqualTo("file.txt");
        assertThat(workspace.resolve("file.txt")).hasContent("new");

        UploadSessionDTO skippedSession = uploadService.create(1L, "", "file.txt", 4L, 4, 1);
        uploadService.putChunk(skippedSession.getUploadId(), 0, bytes("skip"));
        UploadCompleteDTO skipped = uploadService.complete(skippedSession.getUploadId(), "SKIP");
        assertThat(skipped.isSkipped()).isTrue();
        assertThat(skipped.getEntry()).isNull();
        assertThat(skipped.getTargetPath()).isEqualTo("file.txt");
        assertThat(workspace.resolve("file.txt")).hasContent("new");
        assertThatThrownBy(() -> uploadService.get(skippedSession.getUploadId()))
                .isInstanceOf(IllegalArgumentException.class);

        UploadSessionDTO renamedSession = uploadService.create(1L, "", "file.txt", 1L, 1, 1);
        uploadService.putChunk(renamedSession.getUploadId(), 0, bytes("R"));
        UploadCompleteDTO renamed = uploadService.complete(renamedSession.getUploadId(), "RENAME");
        assertThat(renamed.getTargetPath()).isEqualTo("file (1).txt");
        assertThat(workspace.resolve("file (1).txt")).hasContent("R");
    }

    @Test
    void overwriteFailureDoesNotDeleteExistingDirectory() throws IOException {
        Files.createDirectory(workspace.resolve("file.txt"));
        Files.writeString(workspace.resolve("file.txt/keep.txt"), "keep");
        UploadSessionDTO session = uploadService.create(1L, "", "file.txt", 3L, 3, 1);
        uploadService.putChunk(session.getUploadId(), 0, bytes("new"));

        assertThatThrownBy(() -> uploadService.complete(session.getUploadId(), "OVERWRITE"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("覆盖目录");
        assertThat(workspace.resolve("file.txt/keep.txt")).hasContent("keep");
        assertThat(uploadService.get(session.getUploadId()).getReceivedChunks()).containsExactly(0);
        uploadService.cancel(session.getUploadId());
    }

    @Test
    void completesZeroByteFileWithoutChunks() throws IOException {
        UploadSessionDTO session = uploadService.create(1L, "", "empty.txt", 0L, 8192, 0);

        UploadCompleteDTO result = uploadService.complete(session.getUploadId(), "FAIL");

        assertThat(result.getTargetPath()).isEqualTo("empty.txt");
        assertThat(result.getEntry().getSize()).isZero();
        assertThat(workspace.resolve("empty.txt")).isEmptyFile();
    }

    @Test
    void acceptsLargeMetadataWithoutMultipartLimitAndRejectsInvalidArithmetic() throws IOException {
        long tenGiB = 10L * 1024 * 1024 * 1024;
        UploadSessionDTO session = uploadService.create(1L, "", "large.bin", tenGiB,
                WorkspaceUploadService.MAX_CHUNK_SIZE, 1280);
        assertThat(session.getFileSize()).isEqualTo(tenGiB);
        assertThat(session.getReceivedChunks()).isEmpty();
        uploadService.cancel(session.getUploadId());

        assertThatThrownBy(() -> uploadService.create(1L, "", "bad.bin", -1L, 1024, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> uploadService.create(1L, "", "bad.bin", 1L,
                WorkspaceUploadService.MAX_CHUNK_SIZE + 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> uploadService.create(1L, "", "bad.bin", Long.MAX_VALUE,
                WorkspaceUploadService.MAX_CHUNK_SIZE, 1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("不匹配");
    }

    private byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
