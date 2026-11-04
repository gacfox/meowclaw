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

class WriteFileToolTest {

    @TempDir
    Path tempDir;

    private final WriteFileTool tool = new WriteFileTool();

    @Test
    void createsFileAndParentDirectories() throws IOException {
        String result = write("nested/dir/a.txt", "hello", null);

        assertThat(result).isEqualTo("Created nested/dir/a.txt");
        assertThat(read(tempDir.resolve("nested/dir/a.txt"))).isEqualTo("hello");
    }

    @Test
    void overwritesExistingFile() throws IOException {
        write("a.txt", "one", null);

        String result = write("a.txt", "two", null);

        assertThat(result).isEqualTo("Wrote a.txt");
        assertThat(read(tempDir.resolve("a.txt"))).isEqualTo("two");
    }

    @Test
    void appendsToExistingFile() throws IOException {
        write("a.txt", "a", null);

        String result = write("a.txt", "b", true);

        assertThat(result).isEqualTo("Appended a.txt");
        assertThat(read(tempDir.resolve("a.txt"))).isEqualTo("ab");
    }

    @Test
    void appendCreatesMissingFile() throws IOException {
        String result = write("a.txt", "x", true);

        assertThat(result).isEqualTo("Appended a.txt");
        assertThat(read(tempDir.resolve("a.txt"))).isEqualTo("x");
    }

    @Test
    void writesEmptyContent() throws IOException {
        String result = write("a.txt", "", null);

        assertThat(result).isEqualTo("Created a.txt");
        assertThat(Files.size(tempDir.resolve("a.txt"))).isZero();
    }

    @Test
    void writesUnicodeContent() throws IOException {
        String result = write("a.txt", "你好🙂", null);

        assertThat(result).isEqualTo("Created a.txt");
        assertThat(read(tempDir.resolve("a.txt"))).isEqualTo("你好🙂");
    }

    @Test
    void rejectsNullContent() {
        String result = write("a.txt", null, null);

        assertThat(result).isEqualTo("Error: content is required");
        assertThat(tempDir.resolve("a.txt")).doesNotExist();
    }

    @Test
    void rejectsBlankPath() {
        String result = write("  ", "x", null);

        assertThat(result).isEqualTo("Error: path is required");
    }

    @Test
    void rejectsDirectoryTarget() throws IOException {
        Files.createDirectory(tempDir.resolve("dir"));

        String result = write("dir", "x", null);

        assertThat(result).isEqualTo("Error: path is a directory: dir");
    }

    @Test
    void writesBareFilenameAgainstCwd() throws IOException {
        String result = write("a.txt", "hello", null);

        assertThat(result).isEqualTo("Created a.txt");
        assertThat(read(tempDir.resolve("a.txt"))).isEqualTo("hello");
    }

    private AgentContext context() {
        Map<String, Object> variables = new ConcurrentHashMap<>();
        variables.put("cwd", tempDir.toString());
        return AgentContext.builder().variables(variables).build();
    }

    private String write(String path, String content, Boolean append) {
        return tool.write(new WriteFileParam(path, content, append), context());
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
