package com.gacfox.meowclaw.dto.workspace;

import java.util.Locale;

/**
 * 工作区写入操作的统一冲突处理策略。
 */
public enum ConflictStrategy {
    FAIL,
    OVERWRITE,
    SKIP,
    RENAME;

    public static ConflictStrategy from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("冲突策略不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("不支持的冲突策略：" + value);
        }
    }
}
