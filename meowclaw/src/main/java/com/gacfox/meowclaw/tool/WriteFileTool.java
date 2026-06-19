package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
public class WriteFileTool {
    @AgenticTool(name = "write", description = "写入文本文件内容，支持覆盖或追加模式")
    public String write(@AgenticToolParam(name = "param", description = "写入参数") WriteFileParam param,
                        AgentContext ctx) {
        try {
            Path filePath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), param.getPath());
            Files.createDirectories(filePath.getParent());
            StandardOpenOption[] options = Boolean.TRUE.equals(param.getAppend())
                    ? new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.APPEND}
                    : new StandardOpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
            Files.writeString(filePath, param.getContent(), StandardCharsets.UTF_8, options);
            return "OK";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
