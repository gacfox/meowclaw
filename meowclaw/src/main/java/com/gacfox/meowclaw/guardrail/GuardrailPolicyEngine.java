package com.gacfox.meowclaw.guardrail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gacfox.meowclaw.util.ToolPathUtil;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 安全护栏规则引擎：解析策略配置JSON并对工具调用求值
 * <p>
 * 规则自上而下首个命中生效，未命中时使用defaultDecision；tools支持*通配，
 * paths为Ant风格路径模式且支持${cwd}占位符，路径匹配统一忽略大小写
 */
public class GuardrailPolicyEngine {
    public static final String ACTION_ALLOW = "allow";
    public static final String ACTION_ASK = "ask";
    public static final String ACTION_DENY = "deny";
    public static final String CWD_PLACEHOLDER = "${cwd}";

    private static final Set<String> ACTIONS = Set.of(ACTION_ALLOW, ACTION_ASK, ACTION_DENY);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 求值结果
     *
     * @param action           决策动作 allow/ask/deny
     * @param matchedRuleIndex 命中规则的下标，-1表示未命中规则使用默认决策
     */
    public record Decision(String action, int matchedRuleIndex) {
        public boolean isAllow() {
            return ACTION_ALLOW.equals(action);
        }

        public boolean isAsk() {
            return ACTION_ASK.equals(action);
        }
    }

    private record Rule(List<String> tools, List<String> paths, String action) {
    }

    private record PolicyConfig(String defaultDecision, List<Rule> rules) {
    }

    private GuardrailPolicyEngine() {
    }

    /**
     * 校验策略配置JSON合法性
     *
     * @param configJson 策略配置JSON
     * @throws IllegalArgumentException 配置不合法时抛出，信息包含具体原因
     */
    public static void validate(String configJson) {
        parse(configJson);
    }

    /**
     * 对一次工具调用求值
     *
     * @param configJson 策略配置JSON
     * @param toolName   工具名，MCP工具为 serviceName__toolName 形式
     * @param rawPath    工具参数中的路径，无路径参数的工具传null
     * @param cwd        当前工作目录，用于解析相对路径与${cwd}占位符
     * @return 求值结果
     */
    public static Decision evaluate(String configJson, String toolName, String rawPath, String cwd) {
        PolicyConfig config = parse(configJson);
        for (int i = 0; i < config.rules().size(); i++) {
            Rule rule = config.rules().get(i);
            if (matchesAny(rule.tools(), toolName) && matchesPaths(rule, rawPath, cwd)) {
                return new Decision(rule.action(), i);
            }
        }
        return new Decision(config.defaultDecision(), -1);
    }

    private static boolean matchesAny(List<String> patterns, String value) {
        for (String pattern : patterns) {
            if (PATH_MATCHER.match(pattern, value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesPaths(Rule rule, String rawPath, String cwd) {
        if (rule.paths() == null || rule.paths().isEmpty()) {
            return true;
        }
        if (rawPath == null || rawPath.isBlank() || cwd == null || cwd.isBlank()) {
            return false;
        }
        String path = normalizePath(ToolPathUtil.resolve(cwd, rawPath).toString());
        String normalizedCwd = normalizePath(cwd);
        for (String pattern : rule.paths()) {
            String normalizedPattern = normalizePath(pattern.replace(CWD_PLACEHOLDER, normalizedCwd));
            if (PATH_MATCHER.match(normalizedPattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.toLowerCase();
    }

    private static PolicyConfig parse(String configJson) {
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(configJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("策略配置不是合法JSON: " + e.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("策略配置必须是JSON对象");
        }
        JsonNode defaultNode = root.get("defaultDecision");
        if (defaultNode == null || !defaultNode.isTextual() || !ACTIONS.contains(defaultNode.asText())) {
            throw new IllegalArgumentException("defaultDecision必填且必须为 allow/ask/deny 之一");
        }
        List<Rule> rules = new ArrayList<>();
        JsonNode rulesNode = root.get("rules");
        if (rulesNode != null) {
            if (!rulesNode.isArray()) {
                throw new IllegalArgumentException("rules必须是数组");
            }
            for (int i = 0; i < rulesNode.size(); i++) {
                rules.add(parseRule(rulesNode.get(i), i));
            }
        }
        return new PolicyConfig(defaultNode.asText(), rules);
    }

    private static Rule parseRule(JsonNode node, int index) {
        String prefix = "rules[" + index + "]: ";
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(prefix + "必须是JSON对象");
        }
        JsonNode toolsNode = node.get("tools");
        if (toolsNode == null || !toolsNode.isArray() || toolsNode.isEmpty()) {
            throw new IllegalArgumentException(prefix + "tools必填且为非空数组");
        }
        List<String> tools = new ArrayList<>();
        for (JsonNode t : toolsNode) {
            if (!t.isTextual() || t.asText().isBlank()) {
                throw new IllegalArgumentException(prefix + "tools元素必须是非空字符串");
            }
            tools.add(t.asText());
        }
        List<String> paths = new ArrayList<>();
        JsonNode pathsNode = node.get("paths");
        if (pathsNode != null) {
            if (!pathsNode.isArray()) {
                throw new IllegalArgumentException(prefix + "paths必须是数组");
            }
            for (JsonNode p : pathsNode) {
                if (!p.isTextual() || p.asText().isBlank()) {
                    throw new IllegalArgumentException(prefix + "paths元素必须是非空字符串");
                }
                paths.add(p.asText());
            }
        }
        JsonNode actionNode = node.get("action");
        if (actionNode == null || !actionNode.isTextual() || !ACTIONS.contains(actionNode.asText())) {
            throw new IllegalArgumentException(prefix + "action必填且必须为 allow/ask/deny 之一");
        }
        return new Rule(tools, paths, actionNode.asText());
    }
}
