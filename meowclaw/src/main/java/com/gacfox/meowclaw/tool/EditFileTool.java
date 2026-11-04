package com.gacfox.meowclaw.tool;

import com.gacfox.meowclaw.util.ToolPathUtil;
import com.gacfox.proarc.agentic.agent.AgentContext;
import com.gacfox.proarc.agentic.tool.AgenticTool;
import com.gacfox.proarc.agentic.tool.AgenticToolParam;
import org.springframework.stereotype.Component;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 文本文件精确替换工具。
 */
@Component
public class EditFileTool {

    /**
     * 对文本文件执行精确字符串替换。
     *
     * @param param 编辑参数
     * @param ctx   智能体上下文
     * @return 编辑结果或错误信息
     */
    @AgenticTool(name = "edit", description = "对文本文件执行精确字符串替换。old_text 必须与文件内容逐字符一致（包括缩进和空白），不要包含 read 返回的 \"行号: \" 前缀。默认要求 old_text 唯一命中，多处命中会报错；需要替换全部时设置 replace_all=true。old_text 和 new_text 可直接使用 \\n 换行，工具会自动适配文件的 CRLF/LF。")
    public String edit(@AgenticToolParam(name = "param", description = "编辑参数") EditFileParam param,
                       AgentContext ctx) {
        try {
            String path = param.getPath();
            String oldText = param.getOldText();
            String newText = param.getNewText();
            if (path == null || path.isBlank()) {
                return "Error: path is required";
            }
            if (oldText == null || oldText.isEmpty()) {
                return "Error: old_text must not be empty";
            }
            if (newText == null) {
                return "Error: new_text is required; use an empty string to delete matched text";
            }
            if (oldText.equals(newText)) {
                return "Error: old_text and new_text are identical";
            }

            Path file = ToolPathUtil.resolve((String) ctx.getVariables().get("cwd"), path);
            if (!Files.isRegularFile(file)) {
                return "Error: file not found or not a regular file: " + path;
            }

            String original = Files.readString(file, StandardCharsets.UTF_8);
            StringBuilder lfView = new StringBuilder(original.length());
            int[] offsetMap = new int[original.length() + 1];
            int crlfCount = 0;
            int lfCount = 0;
            for (int i = 0; i < original.length(); i++) {
                offsetMap[lfView.length()] = i;
                char c = original.charAt(i);
                if (c == '\r' && i + 1 < original.length() && original.charAt(i + 1) == '\n') {
                    lfView.append('\n');
                    crlfCount++;
                    i++;
                } else {
                    if (c == '\n') {
                        lfCount++;
                    }
                    lfView.append(c);
                }
            }
            offsetMap[lfView.length()] = original.length();
            String fileEol = crlfCount > lfCount ? "\r\n" : "\n";

            String oldTextLf = oldText.replace("\r\n", "\n");
            String newTextLf = newText.replace("\r\n", "\n");
            List<Integer> matchStarts = new ArrayList<>();
            for (int from = 0; ; ) {
                int index = lfView.indexOf(oldTextLf, from);
                if (index < 0) {
                    break;
                }
                matchStarts.add(index);
                from = index + oldTextLf.length();
            }
            if (matchStarts.isEmpty()) {
                return "Error: old_text not found in " + path
                        + " (exact match required; do not include read's line-number prefix)";
            }

            int firstLine = 1;
            for (int i = 0; i < matchStarts.get(0); i++) {
                if (lfView.charAt(i) == '\n') {
                    firstLine++;
                }
            }
            boolean all = Boolean.TRUE.equals(param.getReplaceAll());
            if (matchStarts.size() > 1 && !all) {
                return "Error: found " + matchStarts.size() + " matches (first at line " + firstLine
                        + "); add surrounding context to make old_text unique, or set replace_all=true";
            }

            List<Integer> selectedStarts = all ? matchStarts : List.of(matchStarts.get(0));
            StringBuilder result = new StringBuilder(original);
            for (int i = selectedStarts.size() - 1; i >= 0; i--) {
                int start = selectedStarts.get(i);
                int originalStart = offsetMap[start];
                int originalEnd = offsetMap[start + oldTextLf.length()];
                String region = original.substring(originalStart, originalEnd);
                String eol = region.contains("\r\n") ? "\r\n" : region.contains("\n") ? "\n" : fileEol;
                result.replace(originalStart, originalEnd,
                        "\r\n".equals(eol) ? newTextLf.replace("\n", "\r\n") : newTextLf);
            }
            Files.writeString(file, result, StandardCharsets.UTF_8);

            return "Edited " + path + " (" + selectedStarts.size() + " replacement"
                    + (selectedStarts.size() == 1 ? "" : "s") + ", first at line " + firstLine + ")";
        } catch (MalformedInputException e) {
            return "Error: file is not valid UTF-8: " + param.getPath();
        } catch (Exception e) {
            return "Error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
