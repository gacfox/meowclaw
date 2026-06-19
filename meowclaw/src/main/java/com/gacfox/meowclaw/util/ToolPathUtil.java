package com.gacfox.meowclaw.util;

import java.nio.file.Path;

/**
 * 工具路径解析：相对路径基于 cwd 解析，绝对路径直接使用。不做路径穿越防护。
 */
public final class ToolPathUtil {
    private ToolPathUtil() {}

    public static Path resolve(String cwd, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("path is blank");
        }
        Path path = Path.of(input);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (cwd == null || cwd.isBlank()) {
            return path.normalize();
        }
        return Path.of(cwd).resolve(input).normalize();
    }
}
