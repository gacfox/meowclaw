import type { MemoryEntityWithCount, MemoryGraph, MemoryNodeDTO, PageResult } from "@/types";
import { request } from "./request";

export interface MemoryQuery {
  agentId: number;
  type?: string;
  keyword?: string;
  entityId?: number;
  page: number;
  size: number;
}

export async function listMemories(query: MemoryQuery): Promise<PageResult<MemoryNodeDTO>> {
  const params = new URLSearchParams({
    agentId: String(query.agentId),
    page: String(query.page),
    size: String(query.size),
  });
  if (query.type) params.set("type", query.type);
  if (query.keyword) params.set("keyword", query.keyword);
  if (query.entityId != null) params.set("entityId", String(query.entityId));
  const res = await request<PageResult<MemoryNodeDTO>>(`/api/memory?${params}`);
  return res.data;
}

export async function recallPreview(agentId: number, query: string, limit = 10): Promise<MemoryNodeDTO[]> {
  const params = new URLSearchParams({ agentId: String(agentId), query, limit: String(limit) });
  const res = await request<MemoryNodeDTO[]>(`/api/memory/recall-preview?${params}`);
  return res.data;
}

export async function listMemoryEntities(agentId: number): Promise<MemoryEntityWithCount[]> {
  const res = await request<MemoryEntityWithCount[]>(`/api/memory/entities?agentId=${agentId}`);
  return res.data;
}

export async function getMemoryGraph(agentId: number): Promise<MemoryGraph> {
  const res = await request<MemoryGraph>(`/api/memory/graph?agentId=${agentId}`);
  return res.data;
}

export async function createMemory(data: { agentId: number; type: string; content: string }): Promise<MemoryNodeDTO> {
  const res = await request<MemoryNodeDTO>("/api/memory", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateMemory(id: number, data: { type?: string; content?: string }): Promise<MemoryNodeDTO> {
  const res = await request<MemoryNodeDTO>(`/api/memory/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteMemory(id: number) {
  return request(`/api/memory/${id}`, { method: "DELETE" });
}
