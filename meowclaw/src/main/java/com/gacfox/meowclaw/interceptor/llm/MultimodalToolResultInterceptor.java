package com.gacfox.meowclaw.interceptor.llm;

import com.gacfox.meowclaw.service.ImagePreprocessingService;
import com.gacfox.meowclaw.tool.ImageToolResult;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptor;
import com.gacfox.proarc.agentic.client.interceptor.LlmInterceptorChain;
import com.gacfox.proarc.agentic.model.openai.Message;
import com.gacfox.proarc.agentic.model.openai.ModelInfo;
import com.gacfox.proarc.agentic.model.openai.ModelRequest;
import com.gacfox.proarc.agentic.model.openai.ModelResponse;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多模态请求拦截器：请求发出前把工具结果中的图片标记物化为图片部件，
 * 并限制单次请求的图片Base64总量；不修改执行器消息历史，base64仅存在于单次请求内
 */
@Slf4j
public class MultimodalToolResultInterceptor implements LlmInterceptor {
    static final long MAX_REQUEST_IMAGE_BASE64_BYTES = 8_000_000L;
    private static final String IMAGE_OMITTED = "[图片因单次请求图片总量超过8MB，本次未携带]";

    private final ImageToolResult imageToolResult;
    private final ImagePreprocessingService imagePreprocessingService;

    public MultimodalToolResultInterceptor(ImageToolResult imageToolResult,
                                           ImagePreprocessingService imagePreprocessingService) {
        this.imageToolResult = imageToolResult;
        this.imagePreprocessingService = imagePreprocessingService;
    }

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
        if (messages == null) {
            return request;
        }
        boolean visionSupported = modelInfo.getCapabilities() != null
                && modelInfo.getCapabilities().contains(ModelInfo.CAPABILITY_VISION);
        List<Message> expanded = new ArrayList<>(messages.size() + 2);
        for (Message message : messages) {
            ImageToolResult.ImageRef ref = Message.ROLE_TOOL.equals(message.getRole())
                    ? imageToolResult.parse(message.getContent()) : null;
            if (ref == null) {
                expanded.add(message);
                continue;
            }
            if (!visionSupported) {
                expanded.add(toolText(message, ref.note() + "\n（当前模型不支持视觉能力，图片未携带）"));
                continue;
            }
            try {
                ImagePreprocessingService.ProcessedImage image = imagePreprocessingService.process(
                        Path.of(ref.path()), ref.mimeType());
                String dataUrl = image.toDataUrl();
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
        request.setMessages(limitImagePayload(expanded));
        return request;
    }

    /**
     * 按从新到旧的顺序保留图片，超出总量预算的图片仅替换当次请求中的部件
     */
    private List<Message> limitImagePayload(List<Message> messages) {
        List<ImagePartLocation> images = new ArrayList<>();
        long totalBytes = 0;
        for (int messageIndex = messages.size() - 1; messageIndex >= 0; messageIndex--) {
            if (!(messages.get(messageIndex).getContent() instanceof List<?> parts)) {
                continue;
            }
            for (int partIndex = parts.size() - 1; partIndex >= 0; partIndex--) {
                long bytes = base64Length(parts.get(partIndex));
                if (bytes >= 0) {
                    images.add(new ImagePartLocation(messageIndex, partIndex, bytes));
                    totalBytes += bytes;
                }
            }
        }
        if (totalBytes <= MAX_REQUEST_IMAGE_BASE64_BYTES) {
            return messages;
        }

        Set<ImagePartLocation> retained = new HashSet<>();
        long usedBytes = 0;
        for (ImagePartLocation image : images) {
            if (image.base64Bytes() <= MAX_REQUEST_IMAGE_BASE64_BYTES - usedBytes) {
                retained.add(image);
                usedBytes += image.base64Bytes();
            }
        }

        List<Message> limited = new ArrayList<>(messages.size());
        for (int messageIndex = 0; messageIndex < messages.size(); messageIndex++) {
            limited.add(copyWithoutDroppedImages(messages.get(messageIndex), messageIndex, retained));
        }
        log.warn("单次请求图片Base64总量为{} bytes，超过{} bytes预算，已省略{}张较旧图片",
                totalBytes, MAX_REQUEST_IMAGE_BASE64_BYTES, images.size() - retained.size());
        return limited;
    }

    private Message copyWithoutDroppedImages(Message message, int messageIndex,
                                              Set<ImagePartLocation> retained) {
        if (!(message.getContent() instanceof List<?> parts)) {
            return message;
        }
        List<Object> content = new ArrayList<>(parts.size());
        boolean changed = false;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            Object part = parts.get(partIndex);
            long bytes = base64Length(part);
            if (bytes >= 0
                    && !retained.contains(new ImagePartLocation(messageIndex, partIndex, bytes))) {
                content.add(Map.of("type", "text", "text", IMAGE_OMITTED));
                changed = true;
            } else {
                content.add(part);
            }
        }
        if (!changed) {
            return message;
        }
        return Message.builder()
                .role(message.getRole())
                .content(content)
                .reasoningContent(message.getReasoningContent())
                .toolCalls(message.getToolCalls())
                .toolCallId(message.getToolCallId())
                .build();
    }

    private long base64Length(Object part) {
        if (!(part instanceof Map<?,?> map) || !"image_url".equals(map.get("type"))
                || !(map.get("image_url") instanceof Map<?,?> imageUrl)
                || !(imageUrl.get("url") instanceof String url)
                || !url.startsWith("data:image/")) {
            return -1;
        }
        int comma = url.indexOf(',');
        if (comma < 0 || !url.substring(0, comma).contains(";base64")) {
            return -1;
        }
        return url.length() - comma - 1L;
    }

    private static Message toolText(Message origin, String content) {
        return Message.builder()
                .role(Message.ROLE_TOOL)
                .toolCallId(origin.getToolCallId())
                .content(content)
                .build();
    }

    private record ImagePartLocation(int messageIndex, int partIndex, long base64Bytes) {
    }
}
