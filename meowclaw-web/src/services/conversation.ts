import type { ConversationDTO, ChatEventBatchDTO, PageResult } from "@/types";
import { getToken } from "./request";
import { request } from "./request";

export async function listConversations(agentId: number, page: number, size: number, type?: string): Promise<PageResult<ConversationDTO>> {
  const params = new URLSearchParams({ agentId: String(agentId), page: String(page), size: String(size) });
  if (type) params.set("type", type);
  const res = await request<PageResult<ConversationDTO>>(`/api/conversation?${params}`);
  return res.data;
}

export async function createConversation(agentId: number): Promise<ConversationDTO> {
  const res = await request<ConversationDTO>("/api/conversation", {
    method: "POST",
    body: JSON.stringify({ agentId }),
  });
  return res.data;
}

export async function getConversation(id: number): Promise<ConversationDTO> {
  const res = await request<ConversationDTO>(`/api/conversation/${id}`);
  return res.data;
}

export async function deleteConversation(id: number) {
  return request(`/api/conversation/${id}`, { method: "DELETE" });
}

export async function renameConversation(id: number, title: string): Promise<ConversationDTO> {
  const res = await request<ConversationDTO>(`/api/conversation/${id}/title`, {
    method: "PUT",
    body: JSON.stringify({ title }),
  });
  return res.data;
}

export async function listBatches(conversationId: number): Promise<ChatEventBatchDTO[]> {
  const res = await request<ChatEventBatchDTO[]>(`/api/conversation/${conversationId}/batch`);
  return res.data;
}

export async function chatStream(conversationId: number, content: string, signal?: AbortSignal): Promise<ReadableStream<Uint8Array>> {
  const token = getToken();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`/api/conversation/${conversationId}/chat`, {
    method: "POST",
    headers,
    body: JSON.stringify({ content }),
    signal,
  });
  return res.body!;
}

export async function truncateAfterBatch(conversationId: number, batchId: number, includeSelf = false) {
  return request(`/api/conversation/${conversationId}/batch/${batchId}/truncate?includeSelf=${includeSelf}`, {
    method: "DELETE",
  });
}

export async function waitForTitle(id: number, timeoutMs = 35_000): Promise<string | null> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    const res = await fetch(`/api/conversation/${id}/title-wait`, { headers, signal: controller.signal });
    clearTimeout(timer);
    if (!res.ok) return null;
    const json = await res.json();
    return json.data?.title || null;
  } catch {
    return null;
  }
}
