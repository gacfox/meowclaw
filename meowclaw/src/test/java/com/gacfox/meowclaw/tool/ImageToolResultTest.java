package com.gacfox.meowclaw.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.util.JsonUtil;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImageToolResultTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ImageToolResult imageToolResult =
            new ImageToolResult(objectMapper, new JsonUtil(objectMapper));

    @Test
    void detectsImageByExtension() {
        assertThat(ImageToolResult.detectImageMime(Path.of("a/cat.png"))).isEqualTo("image/png");
        assertThat(ImageToolResult.detectImageMime(Path.of("a/photo.JPG"))).isEqualTo("image/jpeg");
        assertThat(ImageToolResult.detectImageMime(Path.of("a/doc.txt"))).isNull();
        assertThat(ImageToolResult.detectImageMime(Path.of("a/noext"))).isNull();
    }

    @Test
    void buildAndParseRoundTripsWindowsPath() {
        String content = imageToolResult.build(Path.of("H:\\workspace\\cat.png"), "image/png", 12345);

        ImageToolResult.ImageRef ref = imageToolResult.parse(content);

        assertThat(ref).isNotNull();
        assertThat(ref.path()).isEqualTo("H:\\workspace\\cat.png");
        assertThat(ref.mimeType()).isEqualTo("image/png");
        assertThat(ref.bytes()).isEqualTo(12345L);
        assertThat(ref.note()).contains("图片文件已读取");
    }

    @Test
    void parseReturnsNullForNonMarker() {
        assertThat(imageToolResult.parse("普通文本")).isNull();
        assertThat(imageToolResult.parse("<<<image>>>不是合法json")).isNull();
        assertThat(imageToolResult.parse(null)).isNull();
    }

    @Test
    void convertsExpiredImageMarkerToHistoricalText() {
        String content = imageToolResult.build(Path.of("H:\\workspace\\cat.png"), "image/png", 12345);

        assertThat(imageToolResult.toHistoricalText(content))
                .doesNotContain(ImageToolResult.MARKER_PREFIX)
                .contains("历史图片不再随请求携带")
                .contains("cat.png");
        assertThat(imageToolResult.toHistoricalText("普通文本")).isEqualTo("普通文本");
    }
}
