package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 切换当前工作目录（cwd）。后续所有文件/命令工具的相对路径基于新 cwd 解析。
 * cwd 存于 AgentContext.variables，由框架跨 batch 持久化。
 */
@Component
public class CdTool {

    @AgenticTool(name = "cd", description = "切换当前工作目录（cwd）。后续所有文件/命令工具以新cwd为基础路径解析相对路径")
    public String cd(@AgenticToolParam(name = "param", description = "目录切换参数") CdParam param,
                     AgentContext ctx) {
        try {
            if (ctx == null || ctx.getVariables() == null) {
                return "Error: agent context not available";
            }
            String currentCwd = (String) ctx.getVariables().get("cwd");
            Path target = ToolPathUtil.resolve(currentCwd, param.getPath());
            if (!Files.isDirectory(target)) {
                return "Error: not a directory: " + target;
            }
            ctx.getVariables().put("cwd", target.toString());
            return "cwd=" + target;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
