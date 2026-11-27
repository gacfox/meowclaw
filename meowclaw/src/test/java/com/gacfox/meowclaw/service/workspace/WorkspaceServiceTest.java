package com.gacfox.meowclaw.service.workspace;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.exception.UnsupportedWorkspaceFileTypeException;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceServiceTest {

    @TempDir
    Path tempDir;

    private WorkspaceService workspaceService;

    @BeforeEach
    void setUp() {
        AgentRepository agentRepository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setWorkspaceFolder(tempDir.toString());
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent));
        workspaceService = new WorkspaceService(agentRepository);
    }

    @Test
    void resolvesExistingBinaryFileForDownload() throws IOException {
        Path file = tempDir.resolve("nested/archive.bin");
        Files.createDirectories(file.getParent());
        byte[] content = {0, 1, 2, (byte) 255};
        Files.write(file, content);

        Path resolved = workspaceService.resolveFileForDownload(1L, "nested/archive.bin");

        assertThat(resolved).isEqualTo(file.toRealPath());
        assertThat(Files.readAllBytes(resolved)).containsExactly(content);
    }

    @Test
    void rejectsDownloadOutsideWorkspace() {
        assertThatThrownBy(() -> workspaceService.resolveFileForDownload(1L, "../outside.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法路径");
    }

    @Test
    void rejectsDirectoryDownload() throws IOException {
        Files.createDirectory(tempDir.resolve("folder"));

        assertThatThrownBy(() -> workspaceService.resolveFileForDownload(1L, "folder"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能下载目录");
    }

    @Test
    void rejectsMissingFileDownload() {
        assertThatThrownBy(() -> workspaceService.resolveFileForDownload(1L, "missing.bin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    void classifiesTextImageAndPdfPreviews() throws IOException {
        Path text = Files.writeString(tempDir.resolve("note.md"), "你好", StandardCharsets.UTF_8);
        byte[] imageBytes = {0, 1, (byte) 255};
        Path image = Files.write(tempDir.resolve("pixel.PNG"), imageBytes);
        Path pdf = Files.write(tempDir.resolve("manual.PdF"), new byte[]{2, 3, 4});

        WorkspaceFilePreview textPreview = workspaceService.read(1L, "note.md");
        WorkspaceFilePreview imagePreview = workspaceService.read(1L, "pixel.PNG");
        WorkspaceFilePreview pdfPreview = workspaceService.read(1L, "manual.PdF");

        assertThat(textPreview.getKind()).isEqualTo(WorkspaceFilePreview.Kind.TEXT);
        assertThat(textPreview.getMimeType()).isEqualTo("text/plain");
        assertThat(textPreview.getFile()).isEqualTo(text.toRealPath());
        assertThat(textPreview.getSize()).isEqualTo(Files.size(text));
        assertThat(imagePreview.getKind()).isEqualTo(WorkspaceFilePreview.Kind.IMAGE);
        assertThat(imagePreview.getMimeType()).isEqualTo("image/png");
        assertThat(Files.readAllBytes(imagePreview.getFile())).containsExactly(imageBytes);
        assertThat(imagePreview.getSize()).isEqualTo(Files.size(image));
        assertThat(pdfPreview.getKind()).isEqualTo(WorkspaceFilePreview.Kind.PDF);
        assertThat(pdfPreview.getMimeType()).isEqualTo("application/pdf");
        assertThat(pdfPreview.getFile()).isEqualTo(pdf.toRealPath());
        assertThat(pdfPreview.getSize()).isEqualTo(3);
    }

    @Test
    void rejectsUnsupportedAndOversizedTextPreviews() throws IOException {
        Files.write(tempDir.resolve("archive.bin"), new byte[]{1});
        Files.write(tempDir.resolve("large.txt"), new byte[2 * 1024 * 1024 + 1]);

        assertThatThrownBy(() -> workspaceService.read(1L, "archive.bin"))
                .isInstanceOf(UnsupportedWorkspaceFileTypeException.class)
                .hasMessageContaining("不支持预览");
        assertThatThrownBy(() -> workspaceService.read(1L, "large.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("上限 2MB");
    }

    @Test
    void readUsesDownloadPathSafetyChecks() {
        assertThatThrownBy(() -> workspaceService.read(1L, "../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法路径");
    }
}
