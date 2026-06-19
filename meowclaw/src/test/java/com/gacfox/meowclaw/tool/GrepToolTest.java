package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrepToolTest {

    private GrepTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new GrepTool();
    }

    @Test
    void searchSingleFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world\nfoo bar\nhello again\n");
        String result = tool.grep(new GrepParam("hello", file.toString(), null, null));
        assertTrue(result.contains("1:hello world"));
        assertTrue(result.contains("3:hello again"));
        assertEquals(-1, result.indexOf("foo bar"));
    }

    @Test
    void searchDirectory() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "pattern here\n");
        Files.writeString(tempDir.resolve("b.txt"), "no match\n");
        String result = tool.grep(new GrepParam("pattern", tempDir.toString(), null, null));
        assertTrue(result.contains("pattern here"));
        assertEquals(-1, result.indexOf("no match"));
    }

    @Test
    void searchCaseInsensitive() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World\n");
        String result = tool.grep(new GrepParam("hello", file.toString(), true, null));
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void searchCaseSensitive() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "Hello World\n");
        String result = tool.grep(new GrepParam("hello", file.toString(), false, null));
        assertTrue(result.contains("No matches found"));
    }

    @Test
    void searchWithRegex() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "abc123\ndef456\nxyz\n");
        String result = tool.grep(new GrepParam("\\d{3}", file.toString(), null, null));
        assertTrue(result.contains("abc123"));
        assertTrue(result.contains("def456"));
        assertEquals(-1, result.indexOf("xyz"));
    }

    @Test
    void noMatches() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello\n");
        String result = tool.grep(new GrepParam("xyz", file.toString(), null, null));
        assertTrue(result.contains("No matches found"));
    }

    @Test
    void pathNotFound() {
        String result = tool.grep(new GrepParam("test", tempDir.resolve("nope").toString(), null, null));
        assertTrue(result.startsWith("Error:"));
    }

    @Test
    void searchWithoutLineNumbers() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello\n");
        String result = tool.grep(new GrepParam("hello", file.toString(), null, false));
        assertTrue(result.contains("hello"));
        assertEquals(-1, result.indexOf("1:"));
    }
}
