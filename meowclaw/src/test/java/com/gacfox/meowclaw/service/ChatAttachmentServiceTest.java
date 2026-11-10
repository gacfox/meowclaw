package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.ChatAttachmentDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAttachmentServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void preprocessesStoredAttachmentsWhenBuildingImageParts() throws Exception {
        Path source = tempDir.resolve("source.png");
        ImageIO.write(new BufferedImage(2200, 1100, BufferedImage.TYPE_INT_RGB), "png", source.toFile());
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(
                java.nio.file.Files.readAllBytes(source));
        ChatAttachmentService service = new ChatAttachmentService(
                tempDir.resolve("data").toString(), new ImagePreprocessingService());
        List<ChatAttachmentDTO> attachments = service.store(List.of(dataUrl));

        List<Map<String, Object>> parts = service.loadImageParts(attachments);

        assertThat(parts).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, String> imageUrl = (Map<String, String>) parts.get(0).get("image_url");
        String processed = imageUrl.get("url");
        byte[] bytes = Base64.getDecoder().decode(processed.substring(processed.indexOf(',') + 1));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(decoded.getWidth()).isEqualTo(2000);
        assertThat(decoded.getHeight()).isEqualTo(1000);
    }
}
