package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class ReadFileTool {
    @AgenticTool(name = "read", description = "读取文件内容，支持指定行数范围")
    public String read(@AgenticToolParam(name = "param", description = "读取参数") ReadFileParam param,
                       AgentContext ctx) {
        try {
            Path filePath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), param.getPath());
            if (!Files.exists(filePath)) {
                return "Error: file not found: " + filePath;
            }
            if (Files.isDirectory(filePath)) {
                return "Error: path is a directory: " + filePath;
            }
            int offset = param.getOffset() != null ? param.getOffset() : 0;
            int limit = param.getLimit() != null ? param.getLimit() : 2000;

            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            int start = Math.min(offset, lines.size());
            int end = Math.min(start + limit, lines.size());

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < end; i++) {
                sb.append(i + 1).append(": ").append(lines.get(i)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
