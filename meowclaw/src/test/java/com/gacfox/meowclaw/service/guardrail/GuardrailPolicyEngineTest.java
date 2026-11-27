package com.gacfox.meowclaw.service.guardrail;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardrailPolicyEngineTest {
    private final GuardrailPolicyEngine guardrailPolicyEngine =
            new GuardrailPolicyEngine(new ObjectMapper());

    @TempDir
    Path workspace;

    private String cwd() {
        return workspace.toAbsolutePath().toString();
    }

    private String inside(String relativePath) {
        return workspace.resolve(relativePath).toString();
    }

    private String outside(String relativePath) {
        return workspace.resolveSibling(relativePath).toString();
    }

    private Decision evaluate(String config, String toolName, String rawPath) {
        return guardrailPolicyEngine.evaluate(config, toolName, rawPath, cwd());
    }

    private static String config(String defaultDecision, String rules) {
        return "{\"defaultDecision\":\"" + defaultDecision + "\",\"rules\":" + rules + "}";
    }

    @Test
    void fallsBackToDefaultDecisionWhenNoRules() {
        Decision decision = evaluate(config("ask", "[]"), "powershell", null);

        assertThat(decision.getAction()).isEqualTo("ask");
        assertThat(decision.getMatchedRuleIndex()).isEqualTo(-1);
    }

    @Test
    void allowsPathInsideWorkspace() {
        String rules = "[{\"tools\":[\"read\",\"write\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        assertThat(evaluate(config("ask", rules), "read", "src/a.txt").getAction()).isEqualTo("allow");
        assertThat(evaluate(config("ask", rules), "write", inside("out.txt")).getAction()).isEqualTo("allow");
        assertThat(evaluate(config("ask", rules), "read", "../").getAction()).isEqualTo("ask");
    }

    @Test
    void asksForPathOutsideWorkspace() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        Decision decision = evaluate(config("ask", rules), "read", outside("system.ini"));

        assertThat(decision.getAction()).isEqualTo("ask");
        assertThat(decision.getMatchedRuleIndex()).isEqualTo(-1);
    }

    @Test
    void pathMatchingIsCaseAndSlashInsensitive() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";
        String upperCasedCwd = cwd().toUpperCase().replace('\\', '/');

        assertThat(evaluate(config("ask", rules), "read", upperCasedCwd + "/A.TXT").getAction()).isEqualTo("allow");
    }

    @Test
    void toolWithoutPathArgSkipsPathsRule() {
        String rules = "[{\"tools\":[\"bash\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"}]";

        assertThat(evaluate(config("ask", rules), "powershell", null).getAction()).isEqualTo("ask");
    }

    @Test
    void matchesToolNameWildcards() {
        String rules = "[{\"tools\":[\"Tavily__*\"],\"action\":\"deny\"},{\"tools\":[\"*\"],\"action\":\"allow\"}]";

        Decision mcp = evaluate(config("ask", rules), "Tavily__tavily_search", null);
        assertThat(mcp.getAction()).isEqualTo("deny");
        assertThat(mcp.getMatchedRuleIndex()).isEqualTo(0);

        Decision builtin = evaluate(config("ask", rules), "powershell", null);
        assertThat(builtin.getAction()).isEqualTo("allow");
        assertThat(builtin.getMatchedRuleIndex()).isEqualTo(1);
    }

    @Test
    void firstMatchedRuleWins() {
        String rules = "["
                + "{\"tools\":[\"read\"],\"paths\":[\"${cwd}/**\"],\"action\":\"allow\"},"
                + "{\"tools\":[\"read\"],\"action\":\"deny\"}"
                + "]";

        assertThat(evaluate(config("ask", rules), "read", "a.txt").getAction()).isEqualTo("allow");
        assertThat(evaluate(config("ask", rules), "read", outside("b.txt")).getAction()).isEqualTo("deny");
    }

    @Test
    void antStyleSubPathPattern() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"**/config/*.json\"],\"action\":\"deny\"}]";

        assertThat(evaluate(config("allow", rules), "read", "config/app.json").getAction()).isEqualTo("deny");
        assertThat(evaluate(config("allow", rules), "read", inside("config/app.json")).getAction()).isEqualTo("deny");
        assertThat(evaluate(config("allow", rules), "read", "config/app.yaml").getAction()).isEqualTo("allow");
    }

    @Test
    void relativePatternAlsoMatchesOutsideWorkspace() {
        String rules = "[{\"tools\":[\"read\"],\"paths\":[\"**/.env\"],\"action\":\"deny\"}]";

        assertThat(evaluate(config("allow", rules), "read", outside(".env")).getAction()).isEqualTo("deny");
    }

    @Test
    void validatesRejectsInvalidConfigs() {
        assertThatThrownBy(() -> guardrailPolicyEngine.validate("{bad json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guardrailPolicyEngine.validate("{\"rules\":[]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultDecision");
        assertThatThrownBy(() -> guardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":{}}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rules");
        assertThatThrownBy(() -> guardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":[{\"tools\":[],\"action\":\"allow\"}]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tools");
        assertThatThrownBy(() -> guardrailPolicyEngine.validate("{\"defaultDecision\":\"allow\",\"rules\":[{\"tools\":[\"read\"],\"action\":\"maybe\"}]}"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("action");
    }

    @Test
    void validatesAcceptsBuiltinWorkspaceConfig() {
        guardrailPolicyEngine.validate("""
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
