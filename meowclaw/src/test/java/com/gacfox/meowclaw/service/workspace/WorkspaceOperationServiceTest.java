package com.gacfox.meowclaw.service.workspace;

import com.gacfox.meowclaw.dto.workspace.BatchOperationResultDTO;
import com.gacfox.meowclaw.dto.workspace.WorkspacePropertiesDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceOperationServiceTest {
    @TempDir
    Path tempDir;

    private Path workspace;
    private WorkspaceService service;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
        AgentRepository repository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setWorkspaceFolder(workspace.toString());
        when(repository.findById(1L)).thenReturn(Optional.of(agent));
        service = new WorkspaceService(repository);
    }

    @Test
    void copiesFilesAndDirectoriesRecursively() throws IOException {
        Files.createDirectories(workspace.resolve("source/nested"));
        Files.writeString(workspace.resolve("source/a.txt"), "A");
        Files.writeString(workspace.resolve("source/nested/b.txt"), "B");
        Files.createDirectory(workspace.resolve("dest"));

        BatchOperationResultDTO result = service.copy(1L,
                List.of("source/a.txt", "source"), "dest", "FAIL");

        assertThat(result.getResults()).hasSize(2).allMatch(item -> item.isSuccess() && !item.isSkipped());
        assertThat(workspace.resolve("dest/a.txt")).hasContent("A");
        assertThat(workspace.resolve("dest/source/nested/b.txt")).hasContent("B");
        assertThat(workspace.resolve("source/nested/b.txt")).hasContent("B");
    }

    @Test
    void appliesCopyConflictStrategiesAndPreservesExtensionOnRename() throws IOException {
        Files.createDirectories(workspace.resolve("source"));
        Files.createDirectory(workspace.resolve("dest"));
        Files.writeString(workspace.resolve("source/a.txt"), "new");
        Files.writeString(workspace.resolve("dest/a.txt"), "old");

        BatchOperationResultDTO failed = service.copy(1L, List.of("source/a.txt"), "dest", "FAIL");
        assertThat(failed.getResults().get(0).isSuccess()).isFalse();
        assertThat(workspace.resolve("dest/a.txt")).hasContent("old");

        BatchOperationResultDTO skipped = service.copy(1L, List.of("source/a.txt"), "dest", "SKIP");
        assertThat(skipped.getResults().get(0).isSuccess()).isTrue();
        assertThat(skipped.getResults().get(0).isSkipped()).isTrue();
        assertThat(workspace.resolve("dest/a.txt")).hasContent("old");

        BatchOperationResultDTO renamed = service.copy(1L, List.of("source/a.txt"), "dest", "RENAME");
        assertThat(renamed.getResults().get(0).getTargetPath()).isEqualTo("dest/a (1).txt");
        assertThat(workspace.resolve("dest/a (1).txt")).hasContent("new");

        service.copy(1L, List.of("source/a.txt"), "dest", "OVERWRITE");
        assertThat(workspace.resolve("dest/a.txt")).hasContent("new");
    }

    @Test
    void moveBatchRejectsChildDirectoryAndTreatsSameLocationAsNoOp() throws IOException {
        Files.createDirectories(workspace.resolve("dir/sub"));
        Files.writeString(workspace.resolve("dir/file.txt"), "content");

        BatchOperationResultDTO child = service.moveBatch(1L, List.of("dir"), "dir/sub", "OVERWRITE");
        assertThat(child.getResults().get(0).isSuccess()).isFalse();
        assertThat(workspace.resolve("dir/file.txt")).exists();

        BatchOperationResultDTO noOp = service.moveBatch(1L, List.of("dir/file.txt"), "dir", "FAIL");
        assertThat(noOp.getResults().get(0).isSuccess()).isTrue();
        assertThat(noOp.getResults().get(0).isSkipped()).isTrue();
        assertThat(workspace.resolve("dir/file.txt")).hasContent("content");
    }

    @Test
    void moveBatchFailDoesNotOverwrite() throws IOException {
        Files.createDirectory(workspace.resolve("dest"));
        Files.writeString(workspace.resolve("a.txt"), "new");
        Files.writeString(workspace.resolve("dest/a.txt"), "old");

        BatchOperationResultDTO result = service.moveBatch(1L, List.of("a.txt"), "dest", "FAIL");

        assertThat(result.getResults().get(0).isSuccess()).isFalse();
        assertThat(workspace.resolve("a.txt")).hasContent("new");
        assertThat(workspace.resolve("dest/a.txt")).hasContent("old");
    }

    @Test
    void renameSupportsNoOpConflictRenameAndRejectsPathAsName() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "A");
        Files.writeString(workspace.resolve("b.txt"), "B");

        BatchOperationResultDTO failed = service.rename(1L, "a.txt", "b.txt", "FAIL");
        assertThat(failed.getResults()).hasSize(1);
        assertThat(failed.getResults().get(0).isSuccess()).isFalse();

        BatchOperationResultDTO renamed = service.rename(1L, "a.txt", "b.txt", "RENAME");
        assertThat(renamed.getResults().get(0).getTargetPath()).isEqualTo("b (1).txt");
        assertThat(workspace.resolve("b (1).txt")).hasContent("A");

        BatchOperationResultDTO noOp = service.rename(1L, "b.txt", "b.txt", "FAIL");
        assertThat(noOp.getResults().get(0).isSkipped()).isTrue();
        assertThatThrownBy(() -> service.rename(1L, "b.txt", "../escape.txt", "FAIL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件名无效");
    }

    @Test
    void deletesRecursivelyInBatchAndReportsMissingItems() throws IOException {
        Files.createDirectories(workspace.resolve("dir/nested"));
        Files.writeString(workspace.resolve("dir/nested/file.bin"), "data");
        Files.writeString(workspace.resolve("single.txt"), "single");

        BatchOperationResultDTO result = service.deleteBatch(1L,
                List.of("dir", "missing.txt", "single.txt"));

        assertThat(result.getResults()).hasSize(3);
        assertThat(result.getResults().get(0).isSuccess()).isTrue();
        assertThat(result.getResults().get(1).isSuccess()).isFalse();
        assertThat(result.getResults().get(2).isSuccess()).isTrue();
        assertThat(workspace.resolve("dir")).doesNotExist();
        assertThat(workspace.resolve("single.txt")).doesNotExist();
        assertThatThrownBy(() -> service.deleteBatch(1L, List.of("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("根目录");
    }

    @Test
    void calculatesRecursivePropertiesIncludingEmptyDirectories() throws IOException {
        Files.createDirectories(workspace.resolve("stats/empty"));
        Path first = Files.writeString(workspace.resolve("stats/a.txt"), "abc");
        Path second = Files.writeString(workspace.resolve("stats/b.bin"), "wxyz");
        Files.setLastModifiedTime(first, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(second, FileTime.fromMillis(5000));

        WorkspacePropertiesDTO result = service.properties(1L, List.of("stats"));

        assertThat(result.getFiles()).isEqualTo(2);
        assertThat(result.getDirectories()).isEqualTo(2);
        assertThat(result.getTotalSize()).isEqualTo(7);
        long expectedLatest = Files.walk(workspace.resolve("stats"))
                .mapToLong(path -> path.toFile().lastModified()).max().orElse(0);
        assertThat(result.getLastModified()).isEqualTo(expectedLatest);
    }

    @Test
    void rejectsLexicalTraversalForNewOperations() {
        assertThatThrownBy(() -> service.copy(1L, List.of("../outside"), "", "FAIL"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法路径");
        assertThatThrownBy(() -> service.properties(1L, List.of("../outside")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法路径");
        assertThatThrownBy(() -> service.moveBatch(1L, List.of("a"), "../outside", "FAIL"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法路径");
    }

    @Test
    void overwriteRejectsTargetAncestorWithoutDeletingSourceTree() throws IOException {
        Files.createDirectories(workspace.resolve("foo/bar/foo"));
        Files.writeString(workspace.resolve("foo/bar/foo/data.txt"), "keep");

        BatchOperationResultDTO copied = service.copy(1L, List.of("foo/bar/foo"), "", "OVERWRITE");
        assertThat(copied.getResults().get(0).isSuccess()).isFalse();
        assertThat(copied.getResults().get(0).getMessage()).contains("包含来源");
        assertThat(workspace.resolve("foo/bar/foo/data.txt")).hasContent("keep");

        BatchOperationResultDTO moved = service.moveBatch(1L, List.of("foo/bar/foo"), "", "OVERWRITE");
        assertThat(moved.getResults().get(0).isSuccess()).isFalse();
        assertThat(moved.getResults().get(0).getMessage()).contains("包含来源");
        assertThat(workspace.resolve("foo/bar/foo/data.txt")).hasContent("keep");
    }

    @Test
    void preservesLeadingWhitespaceInRelativePaths() throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "plain");
        Files.writeString(workspace.resolve(" a.txt"), "leading");

        assertThat(service.read(1L, " a.txt").getFile()).hasContent("leading");
        assertThat(service.read(1L, "a.txt").getFile()).hasContent("plain");

        service.deleteBatch(1L, List.of(" a.txt"));
        assertThat(workspace.resolve(" a.txt")).doesNotExist();
        assertThat(workspace.resolve("a.txt")).hasContent("plain");
    }

    @Test
    void rejectsSymlinkEscapeForExistingAndFuturePaths() throws IOException {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Path link = workspace.resolve("link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException e) {
            Assumptions.assumeTrue(false, "当前环境不允许创建符号链接");
        }

        assertThatThrownBy(() -> service.properties(1L, List.of("link")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.resolveFileForDownload(1L, "link/secret.txt"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法路径");
        assertThatThrownBy(() -> service.saveText(1L, "link/new.txt", "bad"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("非法路径");
        assertThat(outside.resolve("new.txt")).doesNotExist();
    }
}
