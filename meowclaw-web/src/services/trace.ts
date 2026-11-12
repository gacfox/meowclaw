import type { PageResult, TraceDetail, TraceItem, TraceRetentionPolicy } from "@/types";
import { request } from "./request";

export interface TraceQuery {
  conversationId?: number;
  agentId?: number;
  status?: string;
  keyword?: string;
  startTime?: number;
  endTime?: number;
  page: number;
  size: number;
}

export async function listTraces(query: TraceQuery): Promise<PageResult<TraceItem>> {
  const params = new URLSearchParams({ page: String(query.page), size: String(query.size) });
  if (query.conversationId != null) params.set("conversationId", String(query.conversationId));
  if (query.agentId != null) params.set("agentId", String(query.agentId));
  if (query.status) params.set("status", query.status);
  if (query.keyword) params.set("keyword", query.keyword);
  if (query.startTime != null) params.set("startTime", String(query.startTime));
  if (query.endTime != null) params.set("endTime", String(query.endTime));
  const res = await request<PageResult<TraceItem>>(`/api/trace?${params}`);
  return res.data;
}

export async function getTraceDetail(batchId: number): Promise<TraceDetail> {
  const res = await request<TraceDetail>(`/api/trace/${batchId}`);
  return res.data;
}

export async function deleteTrace(batchId: number): Promise<number> {
  const res = await request<number>(`/api/trace/${batchId}`, { method: "DELETE" });
  return res.data;
}

export async function batchDeleteTraces(batchIds: number[]): Promise<number> {
  const res = await request<number>("/api/trace/batch", {
    method: "DELETE",
    body: JSON.stringify(batchIds),
  });
  return res.data;
}

export async function deleteAllTraces() {
  return request("/api/trace/all", { method: "DELETE" });
}

export async function getTraceObservationEnabled(): Promise<boolean> {
  const res = await request<boolean>("/api/trace/enabled");
  return res.data;
}

export async function updateTraceObservationEnabled(enabled: boolean) {
  return request<boolean>("/api/trace/enabled", {
    method: "PUT",
    body: JSON.stringify({ enabled }),
  });
}

export async function getTraceRetentionPolicy(): Promise<TraceRetentionPolicy> {
  const res = await request<TraceRetentionPolicy>("/api/trace/retention-policy");
  return res.data;
}

export async function updateTraceRetentionPolicy(policy: TraceRetentionPolicy) {
  return request("/api/trace/retention-policy", {
    method: "PUT",
    body: JSON.stringify({ policy }),
  });
}
