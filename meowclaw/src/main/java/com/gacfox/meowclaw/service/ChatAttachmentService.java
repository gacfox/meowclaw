package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.dto.ChatAttachmentDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 聊天图片附件服务：data URL 落盘与多模态消息部件构建
 */
@Service
public class ChatAttachmentService {

    private static final int MAX_IMAGES = 5;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> MIME_EXTENSIONS = Map.of(
            "image/png", ".png",
            "image/jpeg", ".jpg",
            "image/webp", ".webp",
            "image/gif", ".gif"
    );
    private static final String UPLOAD_SUB_DIR = "chat";

    private final Path uploadDir;

    public ChatAttachmentService(@Value("${meowclaw.data-dir}") String dataDir) {
        this.uploadDir = Paths.get(dataDir, "upload", UPLOAD_SUB_DIR);
    }

    /**
     * 解析 data URL 图片并落盘到 data/upload/chat/，返回附件引用列表
     */
    public List<ChatAttachmentDTO> store(List<String> dataUrls) {
        if (dataUrls == null || dataUrls.isEmpty()) {
            return List.of();
        }
        if (dataUrls.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("单次最多上传 " + MAX_IMAGES + " 张图片");
        }
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("创建图片目录失败", e);
        }
        List<ChatAttachmentDTO> attachments = new ArrayList<>();
        for (String dataUrl : dataUrls) {
            ParsedDataUrl parsed = parse(dataUrl);
            String filename = UUID.randomUUID() + MIME_EXTENSIONS.get(parsed.mimeType());
            try {
                Files.write(uploadDir.resolve(filename), parsed.bytes());
            } catch (IOException e) {
                throw new UncheckedIOException("保存图片失败", e);
            }
            attachments.add(new ChatAttachmentDTO(filename, "/upload/" + UPLOAD_SUB_DIR + "/" + filename, parsed.mimeType()));
        }
        return attachments;
    }

    /**
     * 读取已落盘图片并构建 OpenAI 多模态 image_url 部件列表，文件缺失或路径非法的图片跳过
     */
    public List<Map<String, Object>> loadImageParts(List<ChatAttachmentDTO> attachments) {
        Path baseDir = uploadDir.toAbsolutePath().normalize();
        List<Map<String, Object>> parts = new ArrayList<>();
        for (ChatAttachmentDTO attachment : attachments) {
            Path file = baseDir.resolve(attachment.getName()).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file)) {
                continue;
            }
            try {
                String base64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
                parts.add(Map.of(
                        "type", "image_url",
                        "image_url", Map.of("url", "data:" + attachment.getMimeType() + ";base64," + base64)
                ));
            } catch (IOException e) {
                throw new UncheckedIOException("读取图片失败: " + attachment.getName(), e);
            }
        }
        return parts;
    }

    /**
     * 从请求内存中的 data URL 直接构建 image_url 部件（当前轮免读盘）
     */
    public List<Map<String, Object>> toImageParts(List<String> dataUrls) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (String dataUrl : dataUrls) {
            parts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
        }
        return parts;
    }

    private ParsedDataUrl parse(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith("data:")) {
            throw new IllegalArgumentException("图片数据格式非法");
        }
        int comma = dataUrl.indexOf(',');
        int semicolon = dataUrl.indexOf(';');
        if (comma < 0 || semicolon < 0 || semicolon > comma || !dataUrl.substring(semicolon + 1, comma).equals("base64")) {
            throw new IllegalArgumentException("图片数据格式非法，仅支持 base64 data URL");
        }
        String mimeType = dataUrl.substring("data:".length(), semicolon);
        if (!MIME_EXTENSIONS.containsKey(mimeType)) {
            throw new IllegalArgumentException("不支持的图片类型: " + mimeType);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(comma + 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("图片 base64 解码失败", e);
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("单张图片大小不能超过 10MB");
        }
        return new ParsedDataUrl(mimeType, bytes);
    }

    private record ParsedDataUrl(String mimeType, byte[] bytes) {
    }
}
