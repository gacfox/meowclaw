package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    private final ReadFileTool tool = new ReadFileTool();

    @Test
    void readsFileWithOneBasedLineNumbers() throws IOException {
        write("a.txt", "alpha\nbeta\ngamma\n");

        String result = read("a.txt", null, null, false);

        assertThat(result).isEqualTo("Read a.txt, lines 1-3\n1: alpha\n2: beta\n3: gamma\n");
    }

    @Test
    void readsOffsetAndLimit() throws IOException {
        write("a.txt", "alpha\nbeta\ngamma\n");

        String result = read("a.txt", 2, 1, false);

        assertThat(result).isEqualTo("Read a.txt, lines 2-2\n2: beta\n[Output truncated. Continue reading with offset: 3]");
    }

    @Test
    void treatsZeroOffsetAsOne() throws IOException {
        write("a.txt", "alpha\nbeta\ngamma\n");

        String result = read("a.txt", 0, 1, false);

        assertThat(result).isEqualTo("Read a.txt, lines 1-1\n1: alpha\n[Output truncated. Continue reading with offset: 2]");
    }

    @Test
    void addsFooterWhenMoreThanTwoThousandLines() throws IOException {
        write("a.txt", numberedLines(2100));

        String result = read("a.txt", null, null, false);

        assertThat(result).startsWith("Read a.txt, lines 1-2000\n");
        assertThat(result).contains("2000: line2000\n");
        assertThat(result).doesNotContain("2001: line2001");
        assertThat(result).endsWith("[Output truncated. Continue reading with offset: 2001]");
    }

    @Test
    void doesNotAddFooterForExactlyTwoThousandLines() throws IOException {
        write("a.txt", numberedLines(2000));

        String result = read("a.txt", null, null, false);

        assertThat(result).startsWith("Read a.txt, lines 1-2000\n");
        assertThat(result).doesNotContain("Output truncated");
    }

    @Test
    void readsLastPageAtOffset() throws IOException {
        write("a.txt", numberedLines(2100));

        String result = read("a.txt", 2001, 100, false);

        assertThat(result).startsWith("Read a.txt, lines 2001-2100\n");
        assertThat(result).contains("2001: line2001\n");
        assertThat(result).contains("2100: line2100\n");
        assertThat(result).doesNotContain("Output truncated");
    }

    @Test
    void rejectsOffsetOutOfRange() throws IOException {
        write("a.txt", numberedLines(5));

        String result = read("a.txt", 10, null, false);

        assertThat(result).isEqualTo("Error: offset 10 is out of range (file has 5 lines)");
    }

    @Test
    void readsEmptyFile() throws IOException {
        write("a.txt", "");

        String result = read("a.txt", null, null, false);

        assertThat(result).isEqualTo("Read a.txt, 0 lines\n");
    }

    @Test
    void truncatesLongLines() throws IOException {
        write("a.txt", "x".repeat(3000) + "\n");

        String result = read("a.txt", null, null, false);

        assertThat(result).contains("1: " + "x".repeat(2000) + "... (line truncated to 2000 chars)");
    }

    @Test
    void stopsAtByteBudgetAndReturnsNextOffset() throws IOException {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            content.append("y".repeat(1000)).append('\n');
        }
        write("a.txt", content.toString());

        String result = read("a.txt", null, null, false);

        assertThat(result).contains("Output truncated. Continue reading with offset:");
        assertThat(result).doesNotContain("100: " + "y".repeat(1000));
        assertThat(result.length()).isLessThan(60_000);
    }

    @Test
    void keepsCrlfLineNumbers() throws IOException {
        write("a.txt", "a\r\nb\r\n");

        String result = read("a.txt", null, null, false);

        assertThat(result).isEqualTo("Read a.txt, lines 1-2\n1: a\n2: b\n");
    }

    @Test
    void stripsUtf8Bom() throws IOException {
        Path file = tempDir.resolve("a.txt");
        byte[] content = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', '\n'};
        Files.write(file, content);

        String result = read("a.txt", null, null, false);

        assertThat(result).contains("1: a");
        assertThat(result).doesNotContain("\uFEFF");
    }

    @Test
    void rejectsNonUtf8File() throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.write(file, new byte[]{(byte) 0xFF});

        String result = read("a.txt", null, null, false);

        assertThat(result).isEqualTo("Error: file is not valid UTF-8: a.txt");
    }

    @Test
    void rejectsBinaryFile() throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.write(file, new byte[]{'a', 0, 'b'});

        String result = read("a.txt", null, null, false);

        assertThat(result).isEqualTo("Error: cannot read binary file: a.txt");
    }

    @Test
    void rejectsDirectoryWithGlobHint() throws IOException {
        Files.createDirectory(tempDir.resolve("dir"));

        String result = read("dir", null, null, false);

        assertThat(result).contains("use glob");
    }

    @Test
    void rejectsMissingFile() {
        String result = read("missing.txt", null, null, false);

        assertThat(result).isEqualTo("Error: file not found: missing.txt");
    }

    @Test
    void returnsImageMarkerWhenVisionSupported() throws IOException {
        Path file = tempDir.resolve("a.png");
        Files.write(file, new byte[]{1, 2, 3});

        String result = read("a.png", null, null, true);

        assertThat(result).startsWith(ImageToolResult.MARKER_PREFIX);
    }

    @Test
    void rejectsImageWithoutVision() throws IOException {
        Path file = tempDir.resolve("a.png");
        Files.write(file, new byte[]{1, 2, 3});

        String result = read("a.png", null, null, false);

        assertThat(result).contains("vision");
    }

    private AgentContext context(boolean visionSupported) {
        Map<String, Object> variables = new ConcurrentHashMap<>();
        variables.put("cwd", tempDir.toString());
        variables.put("visionSupported", visionSupported);
        return AgentContext.builder().variables(variables).build();
    }

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String read(String name, Integer offset, Integer limit, boolean visionSupported) {
        return tool.read(new ReadFileParam(name, limit, offset), context(visionSupported));
    }

    private String numberedLines(int count) {
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            content.append("line").append(i).append('\n');
        }
        return content.toString();
    }
}
