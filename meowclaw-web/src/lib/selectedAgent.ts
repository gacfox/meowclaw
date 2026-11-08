const SELECTED_AGENT_STORAGE_KEY = "meowclaw_selected_agent_id";

export function readSelectedAgentId(): number | null {
  const raw = localStorage.getItem(SELECTED_AGENT_STORAGE_KEY);
  if (!raw) return null;
  const agentId = Number(raw);
  return Number.isSafeInteger(agentId) && agentId > 0 ? agentId : null;
}

export function writeSelectedAgentId(agentId: number) {
  localStorage.setItem(SELECTED_AGENT_STORAGE_KEY, String(agentId));
}
