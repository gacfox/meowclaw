package com.gacfox.meowclaw.tool;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageToolResultTest {

    @Test
    void detectsImageByExtension() {
        assertThat(ImageToolResult.detectImageMime(Path.of("a/cat.png"))).isEqualTo("image/png");
        assertThat(ImageToolResult.detectImageMime(Path.of("a/photo.JPG"))).isEqualTo("image/jpeg");
        assertThat(ImageToolResult.detectImageMime(Path.of("a/doc.txt"))).isNull();
        assertThat(ImageToolResult.detectImageMime(Path.of("a/noext"))).isNull();
    }

    @Test
    void buildAndParseRoundTripsWindowsPath() {
        String content = ImageToolResult.build(Path.of("H:\\workspace\\cat.png"), "image/png", 12345);

        ImageToolResult.ImageRef ref = ImageToolResult.parse(content);

        assertThat(ref).isNotNull();
        assertThat(ref.path()).isEqualTo("H:\\workspace\\cat.png");
        assertThat(ref.mimeType()).isEqualTo("image/png");
        assertThat(ref.bytes()).isEqualTo(12345L);
        assertThat(ref.note()).contains("图片文件已读取");
    }

    @Test
    void parseReturnsNullForNonMarker() {
        assertThat(ImageToolResult.parse("普通文本")).isNull();
        assertThat(ImageToolResult.parse("<<<image>>>不是合法json")).isNull();
        assertThat(ImageToolResult.parse(null)).isNull();
    }
}
