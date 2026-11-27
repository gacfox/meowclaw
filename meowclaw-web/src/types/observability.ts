import type { ChatAttachmentDTO, ChatEventDTO } from "./chat";

export interface TokenStatsSummary {
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
  totalCachedTokens: number | null;
  cacheHitRate: number | null;
  callCount: number;
}

export interface TokenTopModel {
  llmId: number;
  llmName: string;
  model: string | null;
  callCount: number;
  inputTokens: number;
  outputTokens: number;
}

export interface TokenModelSeries {
  llmId: number;
  llmName: string;
  model: string | null;
  input: number[];
  output: number[];
  total: number[];
  callCount: number[];
}

export interface TokenStatsDTO {
  summary: TokenStatsSummary;
  topModels: TokenTopModel[];
  dates: string[];
  modelSeries: TokenModelSeries[];
}

export type TraceRetentionPolicy = "ALL" | "DAYS_1" | "DAYS_3" | "DAYS_7" | "MONTH_1" | "YEAR_1";

export interface TraceItem {
  batchId: number;
  conversationId: number;
  conversationTitle: string | null;
  agentId: number | null;
  agentName: string | null;
  userContent: string;
  status: string;
  inputTokens: number | null;
  outputTokens: number | null;
  llmCallCount: number;
  createdAt: number;
  completedAt: number | null;
}

export interface LlmCallLogItem {
  id: number;
  purpose: string | null;
  model: string | null;
  status: string;
  errorMessage: string | null;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  cachedTokens: number | null;
  durationMs: number | null;
  requestMessages: string | null;
  responseContent: string | null;
  responseToolCalls: string | null;
  reasoningContent: string | null;
  createdAt: number;
}

export interface TraceDetail {
  batchId: number;
  conversationId: number;
  conversationTitle: string | null;
  agentId: number | null;
  agentName: string | null;
  userContent: string;
  attachments: ChatAttachmentDTO[] | null;
  status: string;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  createdAt: number;
  completedAt: number | null;
  events: ChatEventDTO[];
  llmCalls: LlmCallLogItem[];
}

export interface SystemResourceMetrics {
  cpuUsage: number | null;
  memoryUsage: number | null;
  timestamp: number;
}
