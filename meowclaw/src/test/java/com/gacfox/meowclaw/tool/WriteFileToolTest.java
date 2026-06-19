package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WriteFileToolTest {

    private WriteFileTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new WriteFileTool();
    }

    @Test
    void writeNewFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        String result = tool.write(new WriteFileParam(file.toString(), "hello", null));
        assertEquals("OK", result);
        assertEquals("hello", Files.readString(file));
    }

    @Test
    void writeCreatesParentDirectories() throws IOException {
        Path file = tempDir.resolve("sub/dir/test.txt");
        String result = tool.write(new WriteFileParam(file.toString(), "deep", null));
        assertEquals("OK", result);
        assertEquals("deep", Files.readString(file));
    }

    @Test
    void overwriteExistingFile() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "old");
        tool.write(new WriteFileParam(file.toString(), "new", null));
        assertEquals("new", Files.readString(file));
    }

    @Test
    void appendMode() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "a");
        tool.write(new WriteFileParam(file.toString(), "b", true));
        assertEquals("ab", Files.readString(file));
    }

    @Test
    void writeInvalidPath() {
        String result = tool.write(new WriteFileParam("/nonexistent\0bad/path.txt", "data", null));
        assertTrue(result.startsWith("Error:"));
    }
}
