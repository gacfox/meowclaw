package com.gacfox.meowclaw.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.Map;

/**
 * 图片工具结果的标记封装：工具结果只携带轻量标记（路径/mime/大小），
 * 由 MultimodalToolResultInterceptor 在每次LLM请求发出前物化为多模态图片部件，
 * 避免base64进入消息历史与数据库
 */
public class ImageToolResult {
    public static final String MARKER_PREFIX = "<<<image>>>";
    public static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> MIME_BY_EXT = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif");

    /**
     * 标记内容
     *
     * @param path     图片绝对路径
     * @param mimeType 图片MIME类型
     * @param bytes    文件字节数
     * @param note     展示给模型与界面的说明文本
     */
    public record ImageRef(String path, String mimeType, long bytes, String note) {
    }

    private ImageToolResult() {
    }

    /**
     * 按扩展名探测图片MIME类型，非支持图片返回null
     */
    public static String detectImageMime(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return MIME_BY_EXT.get(name.substring(dot + 1).toLowerCase());
    }

    /**
     * 构建带标记的工具结果文本：首行为机器可读标记，其后为说明文本
     */
    public static String build(Path path, String mimeType, long bytes) {
        String note = "图片文件已读取，内容将随请求附加给模型（" + path + "，" + bytes + " bytes），无需再用文本方式读取。";
        try {
            String json = OBJECT_MAPPER.writeValueAsString(Map.of(
                    "path", path.toString(), "mimeType", mimeType, "bytes", bytes));
            return MARKER_PREFIX + json + "\n" + note;
        } catch (Exception e) {
            throw new IllegalStateException("图片标记序列化失败", e);
        }
    }

    /**
     * 解析工具结果中的图片标记，非标记内容返回null
     */
    public static ImageRef parse(Object content) {
        if (!(content instanceof String text) || !text.startsWith(MARKER_PREFIX)) {
            return null;
        }
        String markerLine = text.substring(MARKER_PREFIX.length());
        int newline = markerLine.indexOf('\n');
        String note = newline >= 0 ? markerLine.substring(newline + 1) : "";
        markerLine = newline >= 0 ? markerLine.substring(0, newline) : markerLine;
        try {
            JsonNode node = OBJECT_MAPPER.readTree(markerLine);
            return new ImageRef(node.get("path").asText(), node.get("mimeType").asText(),
                    node.get("bytes").asLong(), note);
        } catch (Exception e) {
            return null;
        }
    }
}
