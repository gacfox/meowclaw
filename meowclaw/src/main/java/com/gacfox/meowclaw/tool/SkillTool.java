package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作区中指定技能的 SKILL.md 全文。
 * 与技能包管理模块完全解耦——只读文件系统，按 workspacePath/.skill/skillName/SKILL.md 路径访问。
 * workspacePath 由框架从 AgentContext.variables 注入，不由 LLM 提供。
 */
@Component
public class SkillTool {

    @AgenticTool(name = "skill", description = "读取工作区中指定技能的 SKILL.md 完整内容")
    public String skill(@AgenticToolParam(name = "param", description = "技能读取参数") SkillParam param,
                        AgentContext ctx) {
        try {
            if (ctx == null || ctx.getVariables() == null) {
                return "Error: agent context not available";
            }
            Object workspacePath = ctx.getVariables().get("workspacePath");
            if (!(workspacePath instanceof String ws) || ws.isBlank()) {
                return "Error: workspace not configured for this agent";
            }
            Path file = Path.of(ws, ".skill", param.getSkillName(), "SKILL.md");
            if (!Files.exists(file)) {
                return "Error: skill not found in workspace: " + param.getSkillName();
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
