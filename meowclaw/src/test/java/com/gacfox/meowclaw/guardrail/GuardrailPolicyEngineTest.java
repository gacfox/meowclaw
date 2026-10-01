package com.gacfox.meowclaw.guardrail;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailPolicyEngineTest {
    private static final String CWD = "H:\\workspace\\proj";

    private static String config(String defaultDecision, String rules) {
        return "{\"defaultDecision\":\"" + defaultDecision + "\",\"rules\":" + rules + "}";
    }

    @Test
    void fallsBackToDefaultDecisionWhenNoRules() {
        GuardrailPolicyEngine.Decision decision = GuardrailPolicyEngine.evaluate(config("ask", "[]"), "exec", null, CWD);

        assertThat(decision.action()).isEqualTo("ask");
        assertThat(decision.matchedRuleIndex()).isEqualTo(-1);
    }

    @Test
    void allowsPathInsideWorkspace() {
        String rules = "[{\"tools\":[\"read\",\"write\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "read", "src/a.txt", CWD).action())
                .isEqualTo("allow");
        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "write", "H:\\workspace\\proj\\out.txt", CWD).action())
                .isEqualTo("allow");
    }

    @Test
    void asksForPathOutsideWorkspace() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        GuardrailPolicyEngine.Decision decision = GuardrailPolicyEngine.evaluate(config("ask", rules), "read", "C:\\Windows\\system.ini", CWD);

        assertThat(decision.action()).isEqualTo("ask");
        assertThat(decision.matchedRuleIndex()).isEqualTo(-1);
    }

    @Test
    void pathMatchingIsCaseAndSlashInsensitive() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "read", "H:/WORKSPACE/proj/A.TXT", CWD).action())
                .isEqualTo("allow");
    }

    @Test
    void toolWithoutPathArgSkipsPathsRule() {
        String rules = "[{\"tools\":[\"exec\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "exec", null, CWD).action())
                .isEqualTo("ask");
    }

    @Test
    void matchesToolNameWildcards() {
        String rules = "[{\"tools\":[\"Tavily__*\"],\"action\":\"deny\"},{\"tools\":[\"*\"],\"action\":\"allow\"}]";

        GuardrailPolicyEngine.Decision mcp = GuardrailPolicyEngine.evaluate(config("ask", rules), "Tavily__tavily_search", null, CWD);
        assertThat(mcp.action()).isEqualTo("deny");
        assertThat(mcp.matchedRuleIndex()).isEqualTo(0);

        GuardrailPolicyEngine.Decision builtin = GuardrailPolicyEngine.evaluate(config("ask", rules), "exec", null, CWD);
        assertThat(builtin.action()).isEqualTo("allow");
        assertThat(builtin.matchedRuleIndex()).isEqualTo(1);
    }

    @Test
    void firstMatchedRuleWins() {
        String rules = "["
                + "{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"},"
                + "{\"tools\":[\"read\"],\"action\":\"deny\"}"
                + "]";

        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "read", "a.txt", CWD).action())
                .isEqualTo("allow");
        assertThat(GuardrailPolicyEngine.evaluate(config("ask", rules), "read", "C:\\other\\b.txt", CWD).action())
                .isEqualTo("deny");
    }

    @Test
    void antStyleSubPathPattern() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"**/config/*.json\"],\"action\":\"deny\"}]";

        assertThat(GuardrailPolicyEngine.evaluate(config("allow", rules), "read", "config/app.json", CWD).action())
                .isEqualTo("deny");
        assertThat(GuardrailPolicyEngine.evaluate(config("allow", rules), "read", "config/app.yaml", CWD).action())
                .isEqualTo("allow");
    }

    @Test
    void validateRejectsInvalidConfigs() {
        assertThatThrownBy(() -> GuardrailPolicyEngine.validate("{bad json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GuardrailPolicyEngine.validate("{\"rules\":[]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultDecision");
        assertThatThrownBy(() -> GuardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":{}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rules");
        assertThatThrownBy(() -> GuardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":[{\"tools\":[],\"action\":\"allow\"}]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tools");
        assertThatThrownBy(() -> GuardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":[{\"tools\":[\"read\"],\"action\":\"maybe\"}]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("action");
    }

    @Test
    void validateAcceptsBuiltinWorkspaceConfig() {
        GuardrailPolicyEngine.validate("""
                {
                  "defaultDecision": "ask",
                  "rules": [
                    {"tools": ["read", "write", "edit", "glob", "grep", "cd"], "paths": ["${cwd}/**"], "action": "allow"},
                    {"tools": ["memory_write", "memory_recall"], "action": "allow"}
                  ]
                }
                """);
    }
}
