package com.gacfox.meowclaw.service;

import com.gacfox.meowclaw.converter.GuardrailPolicyConverter;
import com.gacfox.meowclaw.dto.GuardrailPolicyDTO;
import com.gacfox.meowclaw.dto.SaveGuardrailPolicyRequest;
import com.gacfox.meowclaw.entity.Conversation;
import com.gacfox.meowclaw.entity.GuardrailPolicy;
import com.gacfox.meowclaw.repository.ConversationRepository;
import com.gacfox.meowclaw.repository.GuardrailPolicyRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GuardrailPolicyService {
    public static final String POLICY_MANUAL_APPROVAL = "手动审批模式";
    public static final String POLICY_WORKSPACE_RW = "工作区读写模式";
    public static final String POLICY_UNLIMITED = "无限制模式";
    public static final String DEFAULT_POLICY_NAME = POLICY_WORKSPACE_RW;

    private static final Map<String, String> BUILTIN_CONFIGS = new LinkedHashMap<>();

    static {
        BUILTIN_CONFIGS.put(POLICY_MANUAL_APPROVAL, """
                {
                  "defaultDecision": "ask",
                  "rules": []
                }
                """);
        BUILTIN_CONFIGS.put(POLICY_WORKSPACE_RW, """
                {
                  "defaultDecision": "ask",
                  "rules": [
                    {
                      "tools": ["read", "write", "edit", "glob", "grep", "cd"],
                      "paths": ["${cwd}/**"],
                      "action": "allow"
                    },
                    {
                      "tools": ["memory_write", "memory_recall"],
                      "action": "allow"
                    }
                  ]
                }
                """);
        BUILTIN_CONFIGS.put(POLICY_UNLIMITED, """
                {
                  "defaultDecision": "allow",
                  "rules": []
                }
                """);
    }

    private final GuardrailPolicyRepository guardrailPolicyRepository;
    private final ConversationRepository conversationRepository;
    private final GuardrailPolicyConverter guardrailPolicyConverter;

    @Autowired
    public GuardrailPolicyService(GuardrailPolicyRepository guardrailPolicyRepository,
                                  ConversationRepository conversationRepository,
                                  GuardrailPolicyConverter guardrailPolicyConverter) {
        this.guardrailPolicyRepository = guardrailPolicyRepository;
        this.conversationRepository = conversationRepository;
        this.guardrailPolicyConverter = guardrailPolicyConverter;
    }

    /**
     * 启动时按名幂等同步内置策略，配置以代码内定义为准
     */
    @PostConstruct
    public void seedBuiltinPolicies() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : BUILTIN_CONFIGS.entrySet()) {
            GuardrailPolicy policy = guardrailPolicyRepository.findByName(entry.getKey()).orElse(null);
            if (policy == null) {
                policy = new GuardrailPolicy();
                policy.setName(entry.getKey());
                policy.setBuiltin(true);
                policy.setCreatedAt(now);
            }
            if (!Boolean.TRUE.equals(policy.getBuiltin()) || !entry.getValue().equals(policy.getConfigJson())) {
                policy.setBuiltin(true);
                policy.setConfigJson(entry.getValue());
            }
            policy.setUpdatedAt(now);
            guardrailPolicyRepository.save(policy);
        }
    }

    @Transactional(readOnly = true)
    public List<GuardrailPolicyDTO> list() {
        return guardrailPolicyRepository.findAll().stream()
                .map(guardrailPolicyConverter::toDTO).toList();
    }

    @Transactional
    public GuardrailPolicyDTO create(SaveGuardrailPolicyRequest req) {
        if (guardrailPolicyRepository.existsByName(req.getName())) {
            throw new IllegalArgumentException("策略名已存在: " + req.getName());
        }
        GuardrailPolicyEngine.validate(req.getConfigJson());
        GuardrailPolicy policy = new GuardrailPolicy();
        policy.setName(req.getName().trim());
        policy.setConfigJson(req.getConfigJson());
        policy.setBuiltin(false);
        long now = System.currentTimeMillis();
        policy.setCreatedAt(now);
        policy.setUpdatedAt(now);
        return guardrailPolicyConverter.toDTO(guardrailPolicyRepository.save(policy));
    }

    @Transactional
    public GuardrailPolicyDTO update(Long id, SaveGuardrailPolicyRequest req) {
        GuardrailPolicy policy = guardrailPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        if (Boolean.TRUE.equals(policy.getBuiltin())) {
            throw new IllegalArgumentException("内置策略不可修改");
        }
        if (!policy.getName().equals(req.getName()) && guardrailPolicyRepository.existsByName(req.getName())) {
            throw new IllegalArgumentException("策略名已存在: " + req.getName());
        }
        GuardrailPolicyEngine.validate(req.getConfigJson());
        policy.setName(req.getName().trim());
        policy.setConfigJson(req.getConfigJson());
        policy.setUpdatedAt(System.currentTimeMillis());
        return guardrailPolicyConverter.toDTO(guardrailPolicyRepository.save(policy));
    }

    @Transactional
    public void delete(Long id) {
        GuardrailPolicy policy = guardrailPolicyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("策略不存在"));
        if (Boolean.TRUE.equals(policy.getBuiltin())) {
            throw new IllegalArgumentException("内置策略不可删除");
        }
        conversationRepository.clearGuardrailPolicy(id);
        guardrailPolicyRepository.delete(policy);
    }

    /**
     * 会话未显式指定策略时的生效默认策略ID：定时任务会话无人值守，默认无限制模式，其余会话默认工作区读写模式
     */
    @Transactional(readOnly = true)
    public Long getEffectiveDefaultPolicyId(String conversationType) {
        String name = "SCHEDULED".equals(conversationType) ? POLICY_UNLIMITED : DEFAULT_POLICY_NAME;
        return guardrailPolicyRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("内置安全护栏策略缺失: " + name))
                .getId();
    }

    /**
     * 解析会话当前生效的策略：子智能体会话继承父会话策略；显式指定的策略优先；
     * 未指定时定时任务会话回退无限制模式（无人值守，审批会导致任务卡死），其余回退默认策略
     */
    @Transactional(readOnly = true)
    public GuardrailPolicy resolvePolicy(Long conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (conv.getParentConversationId() != null) {
            conv = conversationRepository.findById(conv.getParentConversationId()).orElse(conv);
        }
        GuardrailPolicy policy = conv.getGuardrailPolicyId() == null ? null
                : guardrailPolicyRepository.findById(conv.getGuardrailPolicyId()).orElse(null);
        if (policy == null) {
            String fallbackName = "SCHEDULED".equals(conv.getType()) ? POLICY_UNLIMITED : DEFAULT_POLICY_NAME;
            policy = guardrailPolicyRepository.findByName(fallbackName)
                    .orElseThrow(() -> new IllegalStateException("内置安全护栏策略缺失: " + fallbackName));
        }
        return policy;
    }

    @Transactional
    public void updateConversationPolicy(Long conversationId, Long policyId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (policyId != null && !guardrailPolicyRepository.existsById(policyId)) {
            throw new IllegalArgumentException("策略不存在");
        }
        conv.setGuardrailPolicyId(policyId);
        conv.setUpdatedAt(System.currentTimeMillis());
        conversationRepository.save(conv);
    }
}
