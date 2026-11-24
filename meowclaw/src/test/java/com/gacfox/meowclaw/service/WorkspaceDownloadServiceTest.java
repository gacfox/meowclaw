package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.DownloadTicketDTO;
import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.repository.AgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkspaceDownloadServiceTest {
    @TempDir
    Path tempDir;

    private Path workspace;
    private WorkspaceDownloadService downloadService;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
        AgentRepository repository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setWorkspaceFolder(workspace.toString());
        when(repository.findById(1L)).thenReturn(Optional.of(agent));
        downloadService = new WorkspaceDownloadService(new WorkspaceService(repository));
    }

    @Test
    void streamsSingleFileAndConsumesTicketExactlyOnce() throws IOException {
        Files.writeString(workspace.resolve("文件.txt"), "content");
        DownloadTicketDTO ticket = downloadService.prepare(1L, List.of("文件.txt"));

        WorkspaceDownloadService.WorkspaceDownload download = downloadService.open(ticket.getTicket());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        download.body().writeTo(output);

        assertThat(ticket.getFilename()).isEqualTo("文件.txt");
        assertThat(download.contentLength()).isEqualTo(7L);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("content");
        assertThatThrownBy(() -> downloadService.open(ticket.getTicket()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效或已过期");
    }

    @Test
    void streamsUtf8ZipWithStructureAndEmptyDirectories() throws IOException {
        Files.createDirectories(workspace.resolve("目录/空目录"));
        Files.createDirectories(workspace.resolve("目录/子"));
        Files.writeString(workspace.resolve("目录/子/你好.txt"), "你好");
        Files.writeString(workspace.resolve("root.bin"), "root");
        DownloadTicketDTO ticket = downloadService.prepare(1L, List.of("目录", "root.bin"));

        WorkspaceDownloadService.WorkspaceDownload download = downloadService.open(ticket.getTicket());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        download.body().writeTo(output);
        Map<String, byte[]> entries = unzip(output.toByteArray());

        assertThat(ticket.getFilename()).isEqualTo("workspace-download.zip");
        assertThat(download.contentLength()).isNull();
        assertThat(entries).containsKeys("目录/", "目录/空目录/", "目录/子/", "目录/子/你好.txt", "root.bin");
        assertThat(entries.get("目录/子/你好.txt")).isEqualTo("你好".getBytes(StandardCharsets.UTF_8));
        assertThat(entries.keySet()).allMatch(name -> !name.startsWith("/")
                && !name.matches("^[A-Za-z]:.*")
                && List.of(name.split("/")).stream().noneMatch(".."::equals));
    }

    @Test
    void validatesEverySourceBeforeIssuingTicket() throws IOException {
        Files.writeString(workspace.resolve("valid.txt"), "valid");

        assertThatThrownBy(() -> downloadService.prepare(1L, List.of("valid.txt", "../outside.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法路径");
        assertThatThrownBy(() -> downloadService.prepare(1L, List.of("valid.txt", "missing.txt")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("路径不存在");
    }

    private Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), entry.isDirectory() ? new byte[0] : zip.readAllBytes());
            }
        }
        return entries;
    }
}
