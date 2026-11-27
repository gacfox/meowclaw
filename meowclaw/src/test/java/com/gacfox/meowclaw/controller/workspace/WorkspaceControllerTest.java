package com.gacfox.meowclaw.controller.workspace;

import com.gacfox.meowclaw.entity.Agent;
import com.gacfox.meowclaw.exception.GlobalExceptionHandler;
import com.gacfox.meowclaw.repository.AgentRepository;
import com.gacfox.meowclaw.service.workspace.WorkspaceDownloadService;
import com.gacfox.meowclaw.service.workspace.WorkspaceService;
import com.gacfox.meowclaw.service.workspace.WorkspaceUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class WorkspaceControllerTest {
    @TempDir
    Path workspace;

    private MockMvc mockMvc;
    private WorkspaceController controller;

    @BeforeEach
    void setUp() {
        AgentRepository repository = mock(AgentRepository.class);
        Agent agent = new Agent();
        agent.setWorkspaceFolder(workspace.toString());
        when(repository.findById(1L)).thenReturn(Optional.of(agent));
        WorkspaceService workspaceService = new WorkspaceService(repository);
        controller = new WorkspaceController(workspaceService,
                mock(WorkspaceDownloadService.class), mock(WorkspaceUploadService.class));
        mockMvc = standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsRawUtf8TextWithPreviewHeaders() throws Exception {
        String text = "你好，workspace\n第二行";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        Files.write(workspace.resolve("说明.txt"), bytes);

        MockHttpServletResponse response = performPreview("说明.txt", null).getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("text/plain;charset=UTF-8");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isEqualTo(text);
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(bytes.length));
        assertPreviewHeaders(response, "TEXT", "说明.txt");
    }

    @Test
    void returnsOriginalImageBytesAndIgnoresRange() throws Exception {
        byte[] bytes = {(byte) 0x89, 0x50, 0x4e, 0x47, 0, (byte) 255};
        Files.write(workspace.resolve("pixel.png"), bytes);

        MockHttpServletResponse response = performPreview("pixel.png", "bytes=0-1").getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo("image/png");
        assertThat(response.getContentAsByteArray()).containsExactly(bytes);
        assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).isNull();
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(bytes.length));
        assertPreviewHeaders(response, "IMAGE", "pixel.png");
    }

    @Test
    void streamsFullPdfAndSingleClosedRange() throws Exception {
        byte[] bytes = pdfBytes();

        MockHttpServletResponse full = performPreview("MANUAL.PDF", null).getResponse();
        assertThat(full.getStatus()).isEqualTo(200);
        assertThat(full.getContentType()).isEqualTo("application/pdf");
        assertThat(full.getContentAsByteArray()).containsExactly(bytes);
        assertThat(full.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("10");
        assertThat(full.getHeader(HttpHeaders.CONTENT_RANGE)).isNull();
        assertPreviewHeaders(full, "PDF", "MANUAL.PDF");

        MockHttpServletResponse partial = performPreview("MANUAL.PDF", "bytes=2-5").getResponse();
        assertThat(partial.getStatus()).isEqualTo(206);
        assertThat(partial.getContentAsByteArray()).containsExactly(2, 3, 4, 5);
        assertThat(partial.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 2-5/10");
        assertThat(partial.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("4");
        assertPreviewHeaders(partial, "PDF", "MANUAL.PDF");
    }

    @Test
    void supportsSuffixAndOpenEndedPdfRanges() throws Exception {
        pdfBytes();

        MockHttpServletResponse suffix = performPreview("MANUAL.PDF", "bytes=-3").getResponse();
        assertThat(suffix.getStatus()).isEqualTo(206);
        assertThat(suffix.getContentAsByteArray()).containsExactly(7, 8, 9);
        assertThat(suffix.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 7-9/10");
        assertThat(suffix.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("3");

        MockHttpServletResponse openEnded = performPreview("MANUAL.PDF", "bytes=6-").getResponse();
        assertThat(openEnded.getStatus()).isEqualTo(206);
        assertThat(openEnded.getContentAsByteArray()).containsExactly(6, 7, 8, 9);
        assertThat(openEnded.getHeader(HttpHeaders.CONTENT_RANGE)).isEqualTo("bytes 6-9/10");
        assertThat(openEnded.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("4");
    }

    @Test
    void rejectsInvalidMultipleAndUnsatisfiablePdfRanges() throws Exception {
        pdfBytes();

        for (String range : List.of("bytes=invalid", "bytes=0-1,3-4", "bytes=10-11", "bytes=-0", "items=0-1")) {
            MockHttpServletResponse response = performPreview("MANUAL.PDF", range).getResponse();
            assertThat(response.getStatus()).as(range).isEqualTo(416);
            assertThat(response.getHeader(HttpHeaders.CONTENT_RANGE)).as(range).isEqualTo("bytes */10");
            assertThat(response.getContentAsByteArray()).as(range).isEmpty();
        }
    }

    @Test
    void returnsUnifiedJsonErrorForUnsupportedType() throws Exception {
        Files.write(workspace.resolve("archive.bin"), new byte[]{1, 2});

        mockMvc.perform(get("/api/workspace/file").param("agentId", "1").param("path", "archive.bin"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value("该文件类型不支持预览"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void returnsUnifiedJsonErrorForUnsafePath() throws Exception {
        mockMvc.perform(get("/api/workspace/file").param("agentId", "1").param("path", "../outside.txt"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("1"))
                .andExpect(jsonPath("$.message").value(containsString("非法路径")));
    }

    @Test
    void ignoresExpectedClientDisconnectsWithoutFailingStream() throws Exception {
        Path file = workspace.resolve("MANUAL.PDF");
        Files.write(file, pdfBytes());
        StreamingResponseBody body = controller.stream(file, 0, 10);

        for (String message : List.of(
                "你的主机中的软件中止了一个已建立的连接。",
                "An established connection was aborted by the software in your host machine",
                "Broken pipe",
                "Connection reset by peer")) {
            IOException abort = new IOException(message);
            assertThatCode(() -> body.writeTo(failingOutput(abort))).as(message).doesNotThrowAnyException();
        }

        IOException wrapped = new IOException("response failed", new IOException("Connection reset"));
        assertThatCode(() -> body.writeTo(failingOutput(wrapped))).doesNotThrowAnyException();
    }

    @Test
    void propagatesRealStreamFailures() throws Exception {
        Path file = workspace.resolve("MANUAL.PDF");
        Files.write(file, pdfBytes());
        StreamingResponseBody body = controller.stream(file, 0, 10);
        IOException failure = new IOException("disk write failed");

        assertThatThrownBy(() -> body.writeTo(failingOutput(failure))).isSameAs(failure);
    }

    private OutputStream failingOutput(IOException failure) {
        return new OutputStream() {
            @Override
            public void write(int value) throws IOException {
                throw failure;
            }
        };
    }

    private byte[] pdfBytes() throws Exception {
        byte[] bytes = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        Files.write(workspace.resolve("MANUAL.PDF"), bytes);
        return bytes;
    }

    private MvcResult performPreview(String path, String range) throws Exception {
        MockHttpServletRequestBuilder builder = get("/api/workspace/file")
                .param("agentId", "1")
                .param("path", path);
        if (range != null) {
            builder.header(HttpHeaders.RANGE, range);
        }
        MvcResult initial = mockMvc.perform(builder).andReturn();
        if (!initial.getRequest().isAsyncStarted()) {
            return initial;
        }
        return mockMvc.perform(asyncDispatch(initial)).andReturn();
    }

    private void assertPreviewHeaders(MockHttpServletResponse response, String kind, String filename) {
        assertThat(response.getHeader("X-Workspace-Kind")).isEqualTo(kind);
        String contentDisposition = response.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(contentDisposition).startsWith("inline").contains("filename*=UTF-8''");
        assertThat(ContentDisposition.parse(contentDisposition).getFilename()).isEqualTo(filename);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("private,no-store");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.getHeader(HttpHeaders.ACCEPT_RANGES)).isEqualTo("bytes");
    }
}
