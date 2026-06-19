import type { LlmDTO } from "@/types";
import { request } from "./request";

export async function listLlms(): Promise<LlmDTO[]> {
  const res = await request<LlmDTO[]>("/api/llm");
  return res.data;
}

export async function createLlm(data: {
  name: string;
  endpointUrl: string;
  sk?: string;
  model: string;
  maxTokens?: number;
  temperature?: number;
  capabilities?: string;
}): Promise<LlmDTO> {
  const res = await request<LlmDTO>("/api/llm", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateLlm(id: number, data: {
  name?: string;
  endpointUrl?: string;
  sk?: string;
  model?: string;
  maxTokens?: number;
  temperature?: number;
  capabilities?: string;
}): Promise<LlmDTO> {
  const res = await request<LlmDTO>(`/api/llm/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteLlm(id: number) {
  return request(`/api/llm/${id}`, { method: "DELETE" });
}
