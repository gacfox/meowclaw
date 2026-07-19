import type { AgentDTO } from "@/types";
import { request, uploadRequest } from "./request";

export async function listAgents(): Promise<AgentDTO[]> {
  const res = await request<AgentDTO[]>("/api/agent");
  return res.data;
}

export async function createAgent(data: {
  name: string;
  avatarUrl?: string;
  persona?: string;
  enabledTools?: string;
  enabledMcpTools?: string;
  llmId?: number;
  secondaryLlmId?: number;
  embeddingModelId?: number;
  workspaceFolder?: string;
}): Promise<AgentDTO> {
  const res = await request<AgentDTO>("/api/agent", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateAgent(id: number, data: {
  name?: string;
  avatarUrl?: string;
  persona?: string;
  enabledTools?: string;
  enabledMcpTools?: string;
  llmId?: number;
  secondaryLlmId?: number;
  embeddingModelId?: number;
  workspaceFolder?: string;
}): Promise<AgentDTO> {
  const res = await request<AgentDTO>(`/api/agent/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteAgent(id: number) {
  return request(`/api/agent/${id}`, { method: "DELETE" });
}

export async function uploadAgentAvatar(id: number, file: File): Promise<string> {
  return uploadRequest(`/api/agent/${id}/avatar`, file);
}
