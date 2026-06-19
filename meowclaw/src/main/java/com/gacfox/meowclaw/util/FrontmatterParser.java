package com.gacfox.meowclaw.util;

import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.Map;

/**
 * 解析 Markdown 顶部的 YAML frontmatter（一对 {@code ---} 之间的内容）。
 * 无 frontmatter 或解析失败时返回空 Map。
 */
public final class FrontmatterParser {

    private FrontmatterParser() {
    }

    public static Map<String, Object> parse(String md) {
        if (md == null || md.isBlank()) {
            return Collections.emptyMap();
        }
        String trimmed = md.stripLeading();
        if (!trimmed.startsWith("---")) {
            return Collections.emptyMap();
        }
        int start = trimmed.indexOf('\n') + 1;
        if (start <= 0) {
            return Collections.emptyMap();
        }
        int end = trimmed.indexOf("\n---", start);
        if (end < 0) {
            return Collections.emptyMap();
        }
        String yamlBlock = trimmed.substring(start, end);
        try {
            Object parsed = new Yaml().load(yamlBlock);
            if (parsed instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) map;
                return typed;
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyMap();
    }
}
