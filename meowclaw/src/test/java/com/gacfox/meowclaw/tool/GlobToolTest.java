package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobToolTest {

    private GlobTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new GlobTool();
    }

    @Test
    void findFilesByExtension() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "");
        Files.writeString(tempDir.resolve("b.txt"), "");
        Files.writeString(tempDir.resolve("c.java"), "");

        String result = tool.glob(new GlobParam(tempDir.toString(), "*.txt"));
        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("b.txt"));
        assertEquals(-1, result.indexOf("c.java"));
    }

    @Test
    void findInSubdirectory() throws IOException {
        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("nested.java"), "");

        String result = tool.glob(new GlobParam(tempDir.toString(), "**/*.java"));
        assertTrue(result.contains("nested.java"));
    }

    @Test
    void noMatches() throws IOException {
        Files.writeString(tempDir.resolve("a.txt"), "");
        String result = tool.glob(new GlobParam(tempDir.toString(), "*.py"));
        assertTrue(result.contains("No files found"));
    }

    @Test
    void pathNotFound() {
        String result = tool.glob(new GlobParam(tempDir.resolve("nope").toString(), "*.txt"));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("not found"));
    }
}
