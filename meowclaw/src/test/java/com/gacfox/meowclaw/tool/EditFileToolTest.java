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

class EditFileToolTest {

    @TempDir
    Path tempDir;

    private final EditFileTool tool = new EditFileTool();

    @Test
    void replacesSingleLineInLfFile() throws IOException {
        Path file = write("a.txt", "alpha\nbeta\n");

        String result = edit("a.txt", "beta", "gamma");

        assertThat(result).isEqualTo("Edited a.txt (1 replacement, first at line 2)");
        assertThat(read(file)).isEqualTo("alpha\ngamma\n");
    }

    @Test
    void replacesSingleLineInCrlfFile() throws IOException {
        Path file = write("a.txt", "alpha\r\nbeta\r\n");

        String result = edit("a.txt", "beta", "gamma");

        assertThat(result).isEqualTo("Edited a.txt (1 replacement, first at line 2)");
        assertThat(read(file)).isEqualTo("alpha\r\ngamma\r\n");
    }

    @Test
    void matchesLfOldTextAgainstCrlfFileAndKeepsCrlf() throws IOException {
        Path file = write("a.txt", "a\r\nb\r\nc\r\n");

        String result = edit("a.txt", "a\nb", "x\ny");

        assertThat(result).isEqualTo("Edited a.txt (1 replacement, first at line 1)");
        assertThat(read(file)).isEqualTo("x\r\ny\r\nc\r\n");
    }

    @Test
    void matchesCrlfOldTextAgainstLfFileAndKeepsLf() throws IOException {
        Path file = write("a.txt", "a\nb\nc\n");

        String result = edit("a.txt", "a\r\nb", "x\r\ny");

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo("x\ny\nc\n");
    }

    @Test
    void preservesMixedLineEndingsOutsideMatch() throws IOException {
        Path file = write("a.txt", "a\r\nb\nc\r\n");

        String result = edit("a.txt", "b\nc", "X");

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo("a\r\nX\r\n");
    }

    @Test
    void treatsRegexMetacharactersLiterally() throws IOException {
        Path file = write("a.txt", "fooXbar\na.b\n");

        String result = edit("a.txt", "a.b", "Z");

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo("fooXbar\nZ\n");
    }

    @Test
    void writesDollarAndBackslashLiterally() throws IOException {
        Path file = write("a.txt", "value\n");
        String newText = "A$B\\C\n$1";

        String result = edit("a.txt", "value", newText);

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo(newText + "\n");
    }

    @Test
    void rejectsMultipleMatchesWithoutReplaceAll() throws IOException {
        Path file = write("a.txt", "foo\nfoo\n");

        String result = edit("a.txt", "foo", "bar");

        assertThat(result).contains("found 2 matches").contains("first at line 1");
        assertThat(read(file)).isEqualTo("foo\nfoo\n");
    }

    @Test
    void replacesAllMatches() throws IOException {
        Path file = write("a.txt", "foo\r\nfoo\r\n");

        String result = edit("a.txt", "foo", "bar", true);

        assertThat(result).isEqualTo("Edited a.txt (2 replacements, first at line 1)");
        assertThat(read(file)).isEqualTo("bar\r\nbar\r\n");
    }

    @Test
    void rejectsMissingOldText() throws IOException {
        Path file = write("a.txt", "foo\n");

        String result = edit("a.txt", "bar", "baz");

        assertThat(result).contains("old_text not found");
        assertThat(read(file)).isEqualTo("foo\n");
    }

    @Test
    void rejectsEmptyOldTextAndIdenticalText() throws IOException {
        Path file = write("a.txt", "foo\n");

        assertThat(edit("a.txt", "", "bar")).isEqualTo("Error: old_text must not be empty");
        assertThat(edit("a.txt", "foo", "foo")).isEqualTo("Error: old_text and new_text are identical");
        assertThat(read(file)).isEqualTo("foo\n");
    }

    @Test
    void rejectsMissingNewText() throws IOException {
        Path file = write("a.txt", "foo\n");

        String result = edit("a.txt", "foo", null);

        assertThat(result).contains("new_text is required");
        assertThat(read(file)).isEqualTo("foo\n");
    }

    @Test
    void deletesMatchedTextWithEmptyNewText() throws IOException {
        Path file = write("a.txt", "a\nb\nc\n");

        String result = edit("a.txt", "b\n", "");

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo("a\nc\n");
    }

    @Test
    void rejectsMissingFileAndDirectory() throws IOException {
        assertThat(edit("missing.txt", "a", "b")).contains("not found or not a regular file");

        Files.createDirectory(tempDir.resolve("dir"));
        assertThat(edit("dir", "a", "b")).contains("not found or not a regular file");
    }

    @Test
    void keepsUnicodeAndMissingTrailingNewline() throws IOException {
        Path file = write("a.txt", "你好🙂\n结尾");

        String result = edit("a.txt", "结尾", "终");

        assertThat(result).startsWith("Edited a.txt");
        assertThat(read(file)).isEqualTo("你好🙂\n终");
    }

    @Test
    void rejectsLineNumberPrefix() throws IOException {
        Path file = write("a.txt", "foo\n");

        String result = edit("a.txt", "1: foo", "bar");

        assertThat(result).contains("line-number prefix");
        assertThat(read(file)).isEqualTo("foo\n");
    }

    @Test
    void rejectsNonUtf8File() throws IOException {
        Path file = tempDir.resolve("a.txt");
        Files.write(file, new byte[]{(byte) 0xFF});

        String result = edit("a.txt", "a", "b");

        assertThat(result).isEqualTo("Error: file is not valid UTF-8: a.txt");
    }

    private AgentContext context() {
        Map<String, Object> variables = new ConcurrentHashMap<>();
        variables.put("cwd", tempDir.toString());
        return AgentContext.builder().variables(variables).build();
    }

    private Path write(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private String edit(String name, String oldText, String newText) {
        return edit(name, oldText, newText, null);
    }

    private String edit(String name, String oldText, String newText, Boolean replaceAll) {
        return tool.edit(new EditFileParam(name, oldText, newText, replaceAll), context());
    }
}
