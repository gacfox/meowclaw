package com.gacfox.meowclaw.service;

import com.twelvemonkeys.image.ResampleOp;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将发送给模型的图片限制在统一的尺寸和Base64负载预算内。
 */
@Service
public class ImagePreprocessingService {
    public static final int MAX_DIMENSION = 2000;
    public static final long MAX_BASE64_BYTES = 4_500_000L;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final float[] JPEG_QUALITIES = {0.85f, 0.80f, 0.70f, 0.55f, 0.40f};
    private static final int CACHE_SIZE = 16;

    private final Map<CacheKey, ProcessedImage> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<CacheKey, ProcessedImage> eldest) {
                    return size() > CACHE_SIZE;
                }
            });

    /**
     * 读取并预处理图片，合规原图保持不变，JPEG按质量与尺寸逐级压缩。
     *
     * @param source   图片文件
     * @param mimeType 图片MIME类型
     * @return 可直接编码为Data URL的图片
     */
    public ProcessedImage process(Path source, String mimeType) throws IOException {
        Path path = source.toAbsolutePath().normalize();
        CacheKey key = new CacheKey(path, Files.size(path), Files.getLastModifiedTime(path).toMillis(), mimeType);
        ProcessedImage cached = cache.get(key);
        if (cached != null) return cached;

        byte[] original = Files.readAllBytes(path);
        int width;
        int height;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("无法识别图片格式: " + path);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        }
        if ((long) width * height > MAX_SOURCE_PIXELS) {
            throw new IllegalArgumentException("图片像素过大，无法安全处理: " + width + "x" + height);
        }

        ProcessedImage result;
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION && base64Length(original.length) <= MAX_BASE64_BYTES) {
            result = new ProcessedImage(original, mimeType);
        } else {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
            if (image == null) throw new IllegalArgumentException("图片解码失败: " + path);
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
                image = resize(image, Math.max(1, (int) Math.round(width * scale)),
                        Math.max(1, (int) Math.round(height * scale)));
            }
            result = compress(image, mimeType);
        }
        cache.put(key, result);
        return result;
    }

    private ProcessedImage compress(BufferedImage image, String mimeType) throws IOException {
        BufferedImage current = image;
        while (true) {
            if ("image/png".equals(mimeType) || "image/gif".equals(mimeType)) {
                String format = "image/png".equals(mimeType) ? "png" : "gif";
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(current, format, output)) throw new IOException("没有可用的图片编码器: " + format);
                byte[] encoded = output.toByteArray();
                if (base64Length(encoded.length) <= MAX_BASE64_BYTES) {
                    return new ProcessedImage(encoded, mimeType);
                }
            } else {
                BufferedImage rgb = new BufferedImage(current.getWidth(), current.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    graphics.drawImage(current, 0, 0, null);
                } finally {
                    graphics.dispose();
                }
                for (float quality : JPEG_QUALITIES) {
                    byte[] encoded = encodeJpeg(rgb, quality);
                    if (base64Length(encoded.length) <= MAX_BASE64_BYTES) {
                        return new ProcessedImage(encoded, "image/jpeg");
                    }
                }
            }
            if (current.getWidth() == 1 && current.getHeight() == 1) {
                throw new IllegalArgumentException("图片无法压缩到Base64大小限制内");
            }
            current = resize(current, Math.max(1, (int) Math.round(current.getWidth() * 0.75)),
                    Math.max(1, (int) Math.round(current.getHeight() * 0.75)));
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        return new ResampleOp(width, height, ResampleOp.FILTER_LANCZOS).filter(source, null);
    }

    private byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("没有可用的JPEG编码器");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private long base64Length(long byteCount) {
        return 4L * ((byteCount + 2L) / 3L);
    }

    private record CacheKey(Path path, long size, long lastModified, String mimeType) {
    }

    /**
     * 预处理后的图片字节及MIME类型。
     */
    public record ProcessedImage(byte[] bytes, String mimeType) {
        public String toDataUrl() {
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
        }
    }
}
