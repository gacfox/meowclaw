package com.gacfox.meowclaw.interceptor.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.service.ImagePreprocessingService;
import com.gacfox.meowclaw.tool.ImageToolResult;
import com.gacfox.meowclaw.util.JsonUtil;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MultimodalToolResultInterceptorTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ImageToolResult imageToolResult = new ImageToolResult(objectMapper, new JsonUtil(objectMapper));
    private final MultimodalToolResultInterceptor interceptor =
            new MultimodalToolResultInterceptor(imageToolResult, new ImagePreprocessingService());

    private static ModelRequest requestWith(Message... messages) {
        return ModelRequest.builder().messages(new ArrayList<>(List.of(messages))).build();
    }

    private static Message toolMessage(String content) {
        return Message.builder().role(Message.ROLE_TOOL).toolCallId("call-1").content(content).build();
    }

    private static ModelInfo modelInfo(boolean vision) {
        return ModelInfo.builder().capabilities(vision ? List.of("vision") : List.of()).build();
    }

    /**
     * 捕获请求并直通响应的拦截器链尾
     */
    private static class CapturingChain implements LlmInterceptorChain {
        ModelRequest captured;

        @Override
        public ModelResponse nextBlocking(ModelRequest request) {
            captured = request;
            return ModelResponse.builder().build();
        }

        @Override
        public Flux<ModelResponse> nextStreaming(ModelRequest request) {
            captured = request;
            return Flux.empty();
        }
    }

    @Test
    void passesThroughWhenNoMarker() {
        CapturingChain chain = new CapturingChain();
        ModelRequest request = requestWith(toolMessage("普通文本结果"));

        interceptor.interceptBlocking(request, modelInfo(true), chain);

        assertThat(chain.captured.getMessages()).hasSize(1);
        assertThat(chain.captured.getMessages().get(0).getContent()).isEqualTo("普通文本结果");
    }

    @Test
    void expandsMarkerIntoImagePartsForVisionModel() throws Exception {
        Path image = tempDir.resolve("cat.png");
        writePng(image);
        String marked = imageToolResult.build(image, "image/png", Files.size(image));
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(toolMessage(marked)), modelInfo(true), chain);

        List<Message> messages = chain.captured.getMessages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getRole()).isEqualTo(Message.ROLE_TOOL);
        assertThat((String) messages.get(0).getContent()).doesNotContain(ImageToolResult.MARKER_PREFIX);
        Message userMessage = messages.get(1);
        assertThat(userMessage.getRole()).isEqualTo(Message.ROLE_USER);
        @SuppressWarnings("unchecked")
        List<Object> parts = (List<Object>) userMessage.getContent();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(1).toString()).contains("data:image/png;base64,");
    }

    @Test
    void degradesToNoteForNonVisionModel() throws Exception {
        Path image = tempDir.resolve("cat.png");
        writePng(image);
        String marked = imageToolResult.build(image, "image/png", Files.size(image));
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(toolMessage(marked)), modelInfo(false), chain);

        assertThat(chain.captured.getMessages()).hasSize(1);
        assertThat((String) chain.captured.getMessages().get(0).getContent()).contains("不支持视觉");
    }

    @Test
    void degradesToNoteWhenImageFileMissing() {
        String marked = imageToolResult.build(tempDir.resolve("gone.png"), "image/png", 3);
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(toolMessage(marked)), modelInfo(true), chain);

        assertThat(chain.captured.getMessages()).hasSize(1);
        assertThat((String) chain.captured.getMessages().get(0).getContent()).contains("图片读取失败");
    }

    @Test
    void streamingPathAlsoExpands() throws Exception {
        Path image = tempDir.resolve("cat.png");
        writePng(image);
        String marked = imageToolResult.build(image, "image/png", Files.size(image));
        CapturingChain chain = new CapturingChain();

        interceptor.interceptStreaming(requestWith(toolMessage(marked)), modelInfo(true), chain);

        assertThat(chain.captured.getMessages()).hasSize(2);
    }

    @Test
    void limitsUploadedImagesWithoutToolMarkersAndPrefersNewest() {
        Message oldMessage = Message.builder().role(Message.ROLE_USER)
                .content(new ArrayList<>(List.of(imagePart(4_500_000)))).build();
        Message newMessage = Message.builder().role(Message.ROLE_USER)
                .content(new ArrayList<>(List.of(imagePart(4_500_000)))).build();
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(oldMessage, newMessage), modelInfo(true), chain);

        assertThat(isImagePart(parts(chain.captured.getMessages().get(0)).get(0))).isFalse();
        assertThat(parts(chain.captured.getMessages().get(0)).get(0).toString()).contains("超过8MB");
        assertThat(isImagePart(parts(chain.captured.getMessages().get(1)).get(0))).isTrue();
        assertThat(isImagePart(parts(oldMessage).get(0))).isTrue();
    }

    @Test
    void keepsAllImagesAtExactBudgetBoundary() {
        Message first = Message.builder().role(Message.ROLE_USER)
                .content(List.of(imagePart(4_000_000))).build();
        Message second = Message.builder().role(Message.ROLE_USER)
                .content(List.of(imagePart(4_000_000))).build();
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(first, second), modelInfo(true), chain);

        assertThat(isImagePart(parts(chain.captured.getMessages().get(0)).get(0))).isTrue();
        assertThat(isImagePart(parts(chain.captured.getMessages().get(1)).get(0))).isTrue();
    }

    @Test
    void appliesBudgetAcrossUploadedAndReadImages() throws Exception {
        ImagePreprocessingService preprocessing = mock(ImagePreprocessingService.class);
        when(preprocessing.process(any(), any())).thenReturn(
                new ImagePreprocessingService.ProcessedImage(new byte[3_000_000], "image/png"));
        MultimodalToolResultInterceptor budgetInterceptor =
                new MultimodalToolResultInterceptor(imageToolResult, preprocessing);
        Message uploaded = Message.builder().role(Message.ROLE_USER)
                .content(List.of(imagePart(4_500_000))).build();
        String readImage = imageToolResult.build(tempDir.resolve("new.png"), "image/png", 3_000_000);
        CapturingChain chain = new CapturingChain();

        budgetInterceptor.interceptBlocking(requestWith(uploaded, toolMessage(readImage)), modelInfo(true), chain);

        assertThat(isImagePart(parts(chain.captured.getMessages().get(0)).get(0))).isFalse();
        Message materialized = chain.captured.getMessages().get(2);
        assertThat(isImagePart(parts(materialized).get(1))).isTrue();
    }

    @Test
    void streamingPathAlsoLimitsImagePayload() {
        Message oldMessage = Message.builder().role(Message.ROLE_USER)
                .content(List.of(imagePart(4_500_000))).build();
        Message newMessage = Message.builder().role(Message.ROLE_USER)
                .content(List.of(imagePart(4_500_000))).build();
        CapturingChain chain = new CapturingChain();

        interceptor.interceptStreaming(requestWith(oldMessage, newMessage), modelInfo(true), chain);

        assertThat(isImagePart(parts(chain.captured.getMessages().get(0)).get(0))).isFalse();
        assertThat(isImagePart(parts(chain.captured.getMessages().get(1)).get(0))).isTrue();
    }

    private static Map<String, Object> imagePart(int base64Length) {
        return Map.of("type", "image_url", "image_url",
                Map.of("url", "data:image/png;base64," + "a".repeat(base64Length)));
    }

    private static List<?> parts(Message message) {
        return (List<?>) message.getContent();
    }

    private static boolean isImagePart(Object part) {
        return part instanceof Map<?,?> map && "image_url".equals(map.get("type"));
    }

    private void writePng(Path path) throws Exception {
        ImageIO.write(new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB), "png", path.toFile());
    }
}
