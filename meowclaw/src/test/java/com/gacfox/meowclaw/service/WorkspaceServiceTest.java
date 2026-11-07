package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
}
