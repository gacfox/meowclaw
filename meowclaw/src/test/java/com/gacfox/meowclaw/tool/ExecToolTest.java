package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecToolTest {

    private ExecTool tool;

    @BeforeEach
    void setUp() {
        tool = new ExecTool();
    }

    @Test
    void echoCommand() {
        String result = tool.exec(new ExecParam("echo hello", null));
        assertTrue(result.contains("hello"));
    }

    @Test
    void commandWithNonZeroExitCode() {
        String cmd = isWindows() ? "exit /b 1" : "exit 1";
        String result = tool.exec(new ExecParam(cmd, null));
        assertTrue(result.contains("Exit code:"));
    }

    @Test
    void customTimeout() {
        String result = tool.exec(new ExecParam("echo ok", 5));
        assertTrue(result.contains("ok"));
    }

    @Test
    void timeoutExpires() {
        String cmd = isWindows() ? "ping -n 10 127.0.0.1" : "sleep 60";
        String result = tool.exec(new ExecParam(cmd, 1));
        assertTrue(result.contains("timed out") || result.contains("Exit code"));
    }

    @Test
    void invalidCommand() {
        String result = tool.exec(new ExecParam("nonexistent_command_12345", null));
        assertTrue(result.startsWith("Error:") || result.contains("Exit code:"));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
