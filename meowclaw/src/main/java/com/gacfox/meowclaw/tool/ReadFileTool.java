package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.util.ToolPathUtil;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文本文件分页读取工具。
 */
@Component
public class ReadFileTool {

    private static final int MAX_READ_LINES = 2000;
    private static final int MAX_READ_BYTES = 50 * 1024;
    private static final int MAX_LINE_LENGTH = 2000;
    private static final int BINARY_PROBE_BYTES = 8192;

    /**
     * 读取文本文件内容。
     *
     * @param param 读取参数
     * @param ctx   智能体上下文
     * @return 文件内容或错误信息
     */
    @AgenticTool(name = "read", description = "读取文本文件内容。行号从 1 开始，默认最多读取 2000 行/50KB，超出时末尾会提示继续读取的 offset。每行格式为 \"行号: 内容\"，行号和 Read 头部仅供定位，不属于文件内容，不要复制到 edit 的 old_text。图片文件（png/jpeg/webp/gif）在视觉模型下以图片形式读取。")
    public String read(@AgenticToolParam(name = "param", description = "读取参数") ReadFileParam param,
                       AgentContext ctx) {
        String path = param.getPath();
        if (path == null || path.isBlank()) {
            return "Error: path is required";
        }
        try {
            Path file = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), path);
            if (!Files.exists(file)) {
                return "Error: file not found: " + path;
            }
            if (Files.isDirectory(file)) {
                return "Error: path is a directory, use glob to list entries: " + path;
            }

            String imageMime = ImageToolResult.detectImageMime(file);
            if (imageMime != null) {
                if (!Boolean.TRUE.equals(ctx.getVariables().get("visionSupported"))) {
                    return "Error: 目标文件是图片，但当前模型未配置视觉(vision)能力，无法读取: " + path;
                }
                long bytes = Files.size(file);
                if (bytes > ImageToolResult.MAX_IMAGE_BYTES) {
                    return "Error: 图片超过10MB大小限制: " + path;
                }
                return ImageToolResult.build(file, imageMime, bytes);
            }

            try (InputStream in = Files.newInputStream(file)) {
                for (byte b : in.readNBytes(BINARY_PROBE_BYTES)) {
                    if (b == 0) {
                        return "Error: cannot read binary file: " + path;
                    }
                }
            }

            int offset = param.getOffset() == null ? 1 : Math.max(1, param.getOffset());
            int limit = param.getLimit() == null || param.getLimit() <= 0
                    ? MAX_READ_LINES
                    : Math.min(param.getLimit(), MAX_READ_LINES);

            StringBuilder output = new StringBuilder();
            int lineNumber = 0;
            int outputBytes = 0;
            int readLines = 0;
            Integer next = null;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                while (lineNumber < offset - 1 && reader.readLine() != null) {
                    lineNumber++;
                }
                String line;
                while (readLines < limit && (line = reader.readLine()) != null) {
                    lineNumber++;
                    if (lineNumber == 1 && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
                        line = line.substring(1);
                    }
                    if (line.length() > MAX_LINE_LENGTH) {
                        int end = MAX_LINE_LENGTH;
                        if (Character.isHighSurrogate(line.charAt(end - 1))) {
                            end--;
                        }
                        line = line.substring(0, end)
                                + "... (line truncated to " + MAX_LINE_LENGTH + " chars)";
                    }
                    String rendered = lineNumber + ": " + line;
                    int renderedBytes = rendered.getBytes(StandardCharsets.UTF_8).length + 1;
                    if (outputBytes + renderedBytes > MAX_READ_BYTES) {
                        next = lineNumber;
                        break;
                    }
                    output.append(rendered).append('\n');
                    outputBytes += renderedBytes;
                    readLines++;
                }
                if (readLines == 0 && offset > 1) {
                    return "Error: offset " + offset + " is out of range (file has " + lineNumber + " lines)";
                }
                if (next == null && readLines >= limit && reader.readLine() != null) {
                    next = lineNumber + 1;
                }
            }

            StringBuilder result = new StringBuilder();
            if (readLines == 0) {
                result.append("Read ").append(path).append(", 0 lines\n");
            } else {
                result.append("Read ").append(path).append(", lines ")
                        .append(offset).append('-').append(offset + readLines - 1).append('\n');
                result.append(output);
                if (next != null) {
                    result.append("[Output truncated. Continue reading with offset: ").append(next).append(']');
                }
            }
            return result.toString();
        } catch (MalformedInputException e) {
            return "Error: file is not valid UTF-8: " + path;
        } catch (Exception e) {
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
