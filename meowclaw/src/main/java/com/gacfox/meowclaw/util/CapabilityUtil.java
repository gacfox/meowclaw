package com.gacfox.meowclaw.util;

import java.util.Arrays;
import java.util.List;

/**
 * 模型能力标签解析
 */
public final class CapabilityUtil {

    private CapabilityUtil() {
    }

    /**
     * 解析逗号分隔的能力标签字符串为列表
     */
    public static List<String> parse(String capabilities) {
        if (capabilities == null || capabilities.isBlank()) {
            return List.of();
        }
        return Arrays.stream(capabilities.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
