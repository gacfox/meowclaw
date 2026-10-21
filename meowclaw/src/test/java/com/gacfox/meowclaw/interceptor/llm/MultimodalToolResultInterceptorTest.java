package com.gacfox.meowclaw.interceptor.llm;

import com.gacfox.meowclaw.tool.ImageToolResult;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultimodalToolResultInterceptorTest {

    @TempDir
    Path tempDir;

    private final MultimodalToolResultInterceptor interceptor = new MultimodalToolResultInterceptor();

    private static ModelRequest requestWith(Message... messages) {
        return ModelRequest.builder().messages(new ArrayList<>(List.of(messages))).build();
    }

    private static Message toolMessage(String content) {
        return Message.builder().role(Message.ROLE_TOOL).toolCallId("call-1").content(content).build();
    }

    private static ModelInfo modelInfo(boolean vision) {
        return ModelInfo.builder().capabilities(vision ? List.of("vision") : List.of()).build();
    }

    /** 捕获请求并直通响应的拦截器链尾 */
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
        Files.write(image, new byte[]{1, 2, 3});
        String marked = ImageToolResult.build(image, "image/png", 3);
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
        Files.write(image, new byte[]{1, 2, 3});
        String marked = ImageToolResult.build(image, "image/png", 3);
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(toolMessage(marked)), modelInfo(false), chain);

        assertThat(chain.captured.getMessages()).hasSize(1);
        assertThat((String) chain.captured.getMessages().get(0).getContent()).contains("不支持视觉");
    }

    @Test
    void degradesToNoteWhenImageFileMissing() {
        String marked = ImageToolResult.build(tempDir.resolve("gone.png"), "image/png", 3);
        CapturingChain chain = new CapturingChain();

        interceptor.interceptBlocking(requestWith(toolMessage(marked)), modelInfo(true), chain);

        assertThat(chain.captured.getMessages()).hasSize(1);
        assertThat((String) chain.captured.getMessages().get(0).getContent()).contains("图片读取失败");
    }

    @Test
    void streamingPathAlsoExpands() throws Exception {
        Path image = tempDir.resolve("cat.png");
        Files.write(image, new byte[]{1});
        String marked = ImageToolResult.build(image, "image/png", 1);
        CapturingChain chain = new CapturingChain();

        interceptor.interceptStreaming(requestWith(toolMessage(marked)), modelInfo(true), chain);

        assertThat(chain.captured.getMessages()).hasSize(2);
    }
}
