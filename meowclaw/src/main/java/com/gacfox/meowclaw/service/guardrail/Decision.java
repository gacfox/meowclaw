package com.gacfox.meowclaw.service.guardrail;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 求值结果。
 */
@Getter
@AllArgsConstructor
public class Decision {
    /** 决策动作 allow/ask/deny */
    private final String action;
    /** 命中规则的下标，-1表示未命中规则使用默认决策 */
    private final int matchedRuleIndex;

    public boolean isAllow() {
        return "allow".equals(action);
    }

    public boolean isAsk() {
        return "ask".equals(action);
    }
}
