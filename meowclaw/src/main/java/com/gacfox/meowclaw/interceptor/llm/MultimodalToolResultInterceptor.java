package com.gacfox.meowclaw.interceptor.llm;

import com.gacfox.meowclaw.tool.ImageToolResult;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptor;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 多模态工具结果拦截器：请求发出前把工具结果中的图片标记物化为合成user消息（携带图片部件），
 * 非视觉模型或图片不可读时降级为文本注释；不修改执行器消息历史，base64仅存在于单次请求内
 */
@Slf4j
public class MultimodalToolResultInterceptor implements LlmInterceptor {

    @Override
    public ModelResponse interceptBlocking(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        return chain.nextBlocking(expandImages(request, modelInfo));
    }

    @Override
    public Flux<ModelResponse> interceptStreaming(ModelRequest request, ModelInfo modelInfo, LlmInterceptorChain chain) {
        return chain.nextStreaming(expandImages(request, modelInfo));
    }

    private ModelRequest expandImages(ModelRequest request, ModelInfo modelInfo) {
        List<Message> messages = request.getMessages();
        if (messages == null || messages.stream().noneMatch(m -> ImageToolResult.parse(m.getContent()) != null)) {
            return request;
        }
        boolean visionSupported = modelInfo.getCapabilities() != null
                && modelInfo.getCapabilities().contains(ModelInfo.CAPABILITY_VISION);
        List<Message> expanded = new ArrayList<>(messages.size() + 2);
        for (Message message : messages) {
            ImageToolResult.ImageRef ref = Message.ROLE_TOOL.equals(message.getRole())
                    ? ImageToolResult.parse(message.getContent()) : null;
            if (ref == null) {
                expanded.add(message);
                continue;
            }
            if (!visionSupported) {
                expanded.add(toolText(message, ref.note() + "\n（当前模型不支持视觉能力，图片未携带）"));
                continue;
            }
            try {
                byte[] bytes = Files.readAllBytes(Path.of(ref.path()));
                String dataUrl = "data:" + ref.mimeType() + ";base64," + Base64.getEncoder().encodeToString(bytes);
                expanded.add(toolText(message, ref.note()));
                expanded.add(Message.builder()
                        .role(Message.ROLE_USER)
                        .content(List.of(
                                Map.of("type", "text", "text", "[图片内容: " + ref.path() + "]"),
                                Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))))
                        .build());
            } catch (Exception e) {
                log.warn("图片物化失败，降级为文本: {}", ref.path(), e);
                expanded.add(toolText(message, ref.note() + "\n（图片读取失败，未携带: " + e.getMessage() + "）"));
            }
        }
        request.setMessages(expanded);
        return request;
    }

    private static Message toolText(Message origin, String content) {
        return Message.builder()
                .role(Message.ROLE_TOOL)
                .toolCallId(origin.getToolCallId())
                .content(content)
                .build();
    }
}
