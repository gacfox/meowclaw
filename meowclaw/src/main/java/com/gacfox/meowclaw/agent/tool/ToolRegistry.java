package com.gacfox.meowclaw.agent.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolRegistry {
    private static final Map<String, Class<? extends Tool>> INTERNAL_TOOLS = new HashMap<>();
    private final Map<String, Tool> toolInstances = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    static {
        INTERNAL_TOOLS.put("exec", ExecTool.class);
        INTERNAL_TOOLS.put("read", ReadTool.class);
        INTERNAL_TOOLS.put("write", WriteTool.class);
        INTERNAL_TOOLS.put("edit", EditTool.class);
        INTERNAL_TOOLS.put("todo", TodoTool.class);
        INTERNAL_TOOLS.put("glob", GlobTool.class);
        INTERNAL_TOOLS.put("grep", GrepTool.class);
    }

    public List<Tool> loadTools(List<String> toolIds) {
        return toolIds.stream()
                .map(this::getTool)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<Tool> parseAndLoadTools(String enabledToolsJson) {
        if (enabledToolsJson == null || enabledToolsJson.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            List<String> toolIds = objectMapper.readValue(enabledToolsJson, new TypeReference<>() {
            });
            return loadTools(toolIds);
        } catch (Exception e) {
            log.error("解析工具配置失败: {}", enabledToolsJson, e);
            return Collections.emptyList();
        }
    }

    private Tool getTool(String toolId) {
        if (toolInstances.containsKey(toolId)) {
            return toolInstances.get(toolId);
        }

        Class<? extends Tool> toolClass = INTERNAL_TOOLS.get(toolId);
        if (toolClass == null) {
            log.warn("未知工具: {}", toolId);
            return null;
        }

        try {
            Tool tool = toolClass.getDeclaredConstructor().newInstance();
            toolInstances.put(toolId, tool);
            return tool;
        } catch (Exception e) {
            log.error("创建工具实例失败: {}", toolId, e);
            return null;
        }
    }

    public List<Tool> listTools() {
        return INTERNAL_TOOLS.keySet().stream()
                .map(this::getTool)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
