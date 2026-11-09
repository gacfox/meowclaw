package com.gacfox.meowclaw.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPathUtilTest {
    @TempDir
    Path workspace;

    @Test
    void resolveReturnsNormalizedAbsolutePathForRelativeCwd() {
        Path result = ToolPathUtil.resolve("relative-cwd", "nested/../file.txt");

        assertThat(result.isAbsolute()).isTrue();
        assertThat(result).isEqualTo(Path.of("relative-cwd").toAbsolutePath().resolve("file.txt").normalize());
    }

    @Test
    void resolveUsesAbsoluteCwdWithoutChangingPathSemantics() {
        Path result = ToolPathUtil.resolve(workspace.toString(), "nested/../file.txt");

        assertThat(result).isEqualTo(workspace.resolve("file.txt").toAbsolutePath().normalize());
    }
}
