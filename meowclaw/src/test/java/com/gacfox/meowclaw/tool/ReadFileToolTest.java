package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadFileToolTest {

    private ReadFileTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new ReadFileTool();
    }

    @Test
    void readEntireFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello\nworld\n");
        String result = tool.read(new ReadFileParam(file.toString(), null, null));
        assertTrue(result.contains("1: hello"));
        assertTrue(result.contains("2: world"));
    }

    @Test
    void readWithOffset() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\n");
        String result = tool.read(new ReadFileParam(file.toString(), null, 1));
        assertTrue(result.contains("2: line2"));
        assertTrue(result.contains("3: line3"));
        assertEquals(-1, result.indexOf("1: line1"));
    }

    @Test
    void readWithLimit() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "line1\nline2\nline3\nline4\n");
        String result = tool.read(new ReadFileParam(file.toString(), 2, 0));
        assertTrue(result.contains("1: line1"));
        assertTrue(result.contains("2: line2"));
        assertEquals(-1, result.indexOf("3: line3"));
    }

    @Test
    void readNonExistentFile() {
        String result = tool.read(new ReadFileParam(tempDir.resolve("nope.txt").toString(), null, null));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void readDirectory() {
        String result = tool.read(new ReadFileParam(tempDir.toString(), null, null));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("directory"));
    }
}
