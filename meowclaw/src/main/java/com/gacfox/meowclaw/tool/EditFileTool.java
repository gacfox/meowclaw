package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class EditFileTool {
    @AgenticTool(name = "edit", description = "对文本文件进行替换编辑，将指定的旧文本替换为新文本")
    public String edit(@AgenticToolParam(name = "param", description = "编辑参数") EditFileParam param,
                       AgentContext ctx) {
        try {
            Path filePath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), param.getPath());
            if (!Files.exists(filePath)) {
                return "Error: file not found: " + filePath;
            }
            String content = Files.readString(filePath, StandardCharsets.UTF_8);
            String oldText = param.getOldText();
            if (!content.contains(oldText)) {
                return "Error: old_text not found in file";
            }
            String newText = param.getNewText();
            String result;
            if (Boolean.TRUE.equals(param.getReplaceAll())) {
                result = content.replace(oldText, newText);
            } else {
                result = content.replaceFirst(oldText.replace("\\", "\\\\").replace("$", "\\$"), newText);
            }
            Files.writeString(filePath, result, StandardCharsets.UTF_8);
            return "OK";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
