package com.gacfox.meowclaw.tool;

import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class GrepTool {
    private static final int MAX_MATCHES = 100;

    @AgenticTool(name = "grep", description = "在文件内容中搜索匹配正则表达式的行")
    public String grep(@AgenticToolParam(name = "param", description = "搜索参数") GrepParam param,
                       AgentContext ctx) {
        try {
            Path searchPath = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), param.getPath());
            if (!Files.exists(searchPath)) {
                return "Error: path not found: " + searchPath;
            }
            int flags = Boolean.TRUE.equals(param.getIgnoreCase()) ? Pattern.CASE_INSENSITIVE : 0;
            Pattern pattern = Pattern.compile(param.getPattern(), flags);
            boolean showLineNumbers = !Boolean.FALSE.equals(param.getShowLineNumbers());

            List<String> results = new ArrayList<>();
            if (Files.isDirectory(searchPath)) {
                try (Stream<Path> stream = Files.walk(searchPath)) {
                    stream.filter(Files::isRegularFile)
                            .forEach(file -> searchFile(file, pattern, showLineNumbers, results));
                }
            } else {
                searchFile(searchPath, pattern, showLineNumbers, results);
            }

            if (results.isEmpty()) {
                return "No matches found for pattern: " + param.getPattern();
            }
            return String.join("\n", results);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void searchFile(Path file, Pattern pattern, boolean showLineNumbers, List<String> results) {
        if (results.size() >= MAX_MATCHES) return;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size() && results.size() < MAX_MATCHES; i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    results.add(showLineNumbers ? (i + 1) + ":" + lines.get(i) : lines.get(i));
                }
            }
        } catch (Exception ignored) {
            // skip unreadable files
        }
    }
}
