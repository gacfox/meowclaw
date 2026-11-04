package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.util.ToolPathUtil;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文本文件写入工具。
 */
@Component
public class WriteFileTool {

    /**
     * 写入文本文件。
     *
     * @param param 写入参数
     * @param ctx   智能体上下文
     * @return 写入结果或错误信息
     */
    @AgenticTool(name = "write", description = "写入文本文件（UTF-8）。默认覆盖已有文件；append=true 时追加到文件末尾，文件不存在则创建。会自动创建父目录。仅用于创建新文件或整体重写，局部修改请使用 edit。")
    public String write(@AgenticToolParam(name = "param", description = "写入参数") WriteFileParam param,
                        AgentContext ctx) {
        String path = param.getPath();
        String content = param.getContent();
        if (path == null || path.isBlank()) {
            return "Error: path is required";
        }
        if (content == null) {
            return "Error: content is required";
        }
        try {
            Path filePath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), path);
            if (Files.isDirectory(filePath)) {
                return "Error: path is a directory: " + path;
            }
            Path parent = filePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean append = Boolean.TRUE.equals(param.getAppend());
            boolean existed = Files.exists(filePath);
            StandardOpenOption[] options = append
                    ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
            Files.writeString(filePath, content, StandardCharsets.UTF_8, options);
            String action = append ? "Appended" : existed ? "Wrote" : "Created";
            return action + " " + path;
        } catch (Exception e) {
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
