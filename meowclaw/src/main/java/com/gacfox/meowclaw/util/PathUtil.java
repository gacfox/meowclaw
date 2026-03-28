package com.gacfox.meowclaw.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathUtil {
    private PathUtil() {
    }

    public static Path resolvePath(Path baseDir, String userPath) {
        Path raw = Paths.get(userPath);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        Path base = baseDir != null
                ? baseDir
                : Paths.get(".").toAbsolutePath().normalize();
        return base.resolve(raw).normalize();
    }
}
