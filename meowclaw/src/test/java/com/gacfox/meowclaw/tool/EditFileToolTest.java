package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditFileToolTest {

    private EditFileTool tool;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new EditFileTool();
    }

    @Test
    void replaceFirstOccurrence() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "foo bar foo");
        String result = tool.edit(new EditFileParam(file.toString(), "foo", "baz", null));
        assertEquals("OK", result);
        assertEquals("baz bar foo", Files.readString(file));
    }

    @Test
    void replaceAllOccurrences() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "foo bar foo");
        String result = tool.edit(new EditFileParam(file.toString(), "foo", "baz", true));
        assertEquals("OK", result);
        assertEquals("baz bar baz", Files.readString(file));
    }

    @Test
    void oldTextNotFound() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello");
        String result = tool.edit(new EditFileParam(file.toString(), "xyz", "abc", null));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void fileNotFound() {
        String result = tool.edit(new EditFileParam(tempDir.resolve("nope.txt").toString(), "a", "b", null));
        assertTrue(result.startsWith("Error:"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void replaceWithRegexSpecialChars() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "price: $100");
        String result = tool.edit(new EditFileParam(file.toString(), "$100", "200", null));
        assertEquals("OK", result);
        assertEquals("price: 200", Files.readString(file));
    }
}
