import type { ConversationDTO, GuardrailPolicyDTO } from "@/types";
import { request } from "./request";

export async function listGuardrailPolicies(): Promise<GuardrailPolicyDTO[]> {
  const res = await request<GuardrailPolicyDTO[]>("/api/guardrail/policy");
  return res.data;
}

export async function createGuardrailPolicy(name: string, configJson: string): Promise<GuardrailPolicyDTO> {
  const res = await request<GuardrailPolicyDTO>("/api/guardrail/policy", {
    method: "POST",
    body: JSON.stringify({ name, configJson }),
  });
  return res.data;
}

export async function updateGuardrailPolicy(id: number, name: string, configJson: string): Promise<GuardrailPolicyDTO> {
  const res = await request<GuardrailPolicyDTO>(`/api/guardrail/policy/${id}`, {
    method: "PUT",
    body: JSON.stringify({ name, configJson }),
  });
  return res.data;
}

export async function deleteGuardrailPolicy(id: number) {
  return request(`/api/guardrail/policy/${id}`, { method: "DELETE" });
}

export async function setConversationGuardrailPolicy(conversationId: number, policyId: number | null): Promise<ConversationDTO> {
  const res = await request<ConversationDTO>(`/api/conversation/${conversationId}/guardrail-policy`, {
    method: "PUT",
    body: JSON.stringify({ policyId }),
  });
  return res.data;
}

export async function decideApproval(conversationId: number, toolCallId: string, approve: boolean) {
  return request(`/api/conversation/${conversationId}/approvals/${toolCallId}`, {
    method: "POST",
    body: JSON.stringify({ decision: approve ? "approve" : "reject" }),
  });
}
