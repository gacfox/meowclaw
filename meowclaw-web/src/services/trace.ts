import type { PageResult, TraceDetail, TraceItem } from "@/types";
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
