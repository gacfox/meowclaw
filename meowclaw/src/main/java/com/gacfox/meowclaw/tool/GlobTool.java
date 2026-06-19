package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Component
public class GlobTool {
    private static final int MAX_RESULTS = 100;

    @AgenticTool(name = "glob", description = "快速搜索匹配指定模式的文件路径")
    public String glob(@AgenticToolParam(name = "param", description = "搜索参数") GlobParam param,
                       AgentContext ctx) {
        try {
            Path basePath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), param.getPath());
            if (!Files.exists(basePath)) {
                return "Error: path not found: " + basePath;
            }
            String pattern = param.getPattern();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            List<String> results = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(basePath)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> matcher.matches(basePath.relativize(p)))
                        .limit(MAX_RESULTS)
                        .forEach(p -> results.add(p.toString()));
            }
            if (results.isEmpty()) {
                return "No files found matching pattern: " + pattern;
            }
            return String.join("\n", results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
