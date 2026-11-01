export interface AgentPreferences {
  reasoningEffort?: string;
  guardrailPolicyId?: number;
}

const STORAGE_PREFIX = "meowclaw_agent_preferences:";

export function readAgentPreferences(agentId: number): AgentPreferences {
  try {
    const raw = localStorage.getItem(`${STORAGE_PREFIX}${agentId}`);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as Partial<AgentPreferences>;
    return {
      reasoningEffort: typeof parsed.reasoningEffort === "string" && parsed.reasoningEffort.trim()
        ? parsed.reasoningEffort
        : undefined,
      guardrailPolicyId: typeof parsed.guardrailPolicyId === "number" && Number.isInteger(parsed.guardrailPolicyId)
        ? parsed.guardrailPolicyId
        : undefined,
    };
  } catch {
    return {};
  }
}

export function writeAgentPreferences(agentId: number, patch: Partial<AgentPreferences>): AgentPreferences {
  const next = { ...readAgentPreferences(agentId), ...patch };
  localStorage.setItem(`${STORAGE_PREFIX}${agentId}`, JSON.stringify(next));
  return next;
}
