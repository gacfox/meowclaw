package com.gacfox.meowclaw.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ImagePreprocessingServiceTest {
    @TempDir
    Path tempDir;

    private final ImagePreprocessingService service = new ImagePreprocessingService();

    @Test
    void keepsCompliantOriginalUnchanged() throws Exception {
        Path file = tempDir.resolve("small.png");
        ImageIO.write(new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        byte[] original = Files.readAllBytes(file);

        ImagePreprocessingService.ProcessedImage result = service.process(file, "image/png");

        assertThat(result.bytes()).isEqualTo(original);
        assertThat(result.mimeType()).isEqualTo("image/png");
    }

    @Test
    void resizesOversizedPngWithinBothBudgets() throws Exception {
        Path file = tempDir.resolve("large.png");
        ImageIO.write(new BufferedImage(2500, 1000, BufferedImage.TYPE_INT_ARGB), "png", file.toFile());

        ImagePreprocessingService.ProcessedImage result = service.process(file, "image/png");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(result.mimeType()).isEqualTo("image/png");
        assertThat(decoded.getWidth()).isEqualTo(2000);
        assertThat(decoded.getHeight()).isEqualTo(800);
        assertThat(Base64.getEncoder().encodeToString(result.bytes()).length())
                .isLessThanOrEqualTo((int) ImagePreprocessingService.MAX_BASE64_BYTES);
    }

    @Test
    void compressesNoisyJpegWithinBothBudgets() throws Exception {
        Path file = tempDir.resolve("noise.jpg");
        int width = 2400;
        int height = 1800;
        int[] pixels = new int[width * height];
        Random random = new Random(42);
        Arrays.setAll(pixels, index -> random.nextInt(0x1000000));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        ImageIO.write(image, "jpeg", file.toFile());

        ImagePreprocessingService.ProcessedImage result = service.process(file, "image/jpeg");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.bytes()));

        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThat(decoded.getWidth()).isLessThanOrEqualTo(ImagePreprocessingService.MAX_DIMENSION);
        assertThat(decoded.getHeight()).isLessThanOrEqualTo(ImagePreprocessingService.MAX_DIMENSION);
        assertThat(Base64.getEncoder().encodeToString(result.bytes()).length())
                .isLessThanOrEqualTo((int) ImagePreprocessingService.MAX_BASE64_BYTES);
    }
}
