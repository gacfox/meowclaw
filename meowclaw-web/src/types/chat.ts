import type { ProjectDTO } from "./project";

export interface ConversationDTO {
  id: number;
  agentId: number;
  title: string | null;
  type: string | null;
  guardrailPolicyId: number | null;
  pinned: boolean;
  projectId: number | null;
  contextPromptTokens: number | null;
  contextLength: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface SidebarGroupsDTO {
  pinned: ConversationDTO[];
  projects: ProjectDTO[];
}

export interface ConversationHistoryDTO {
  id: number;
  agentId: number;
  agentName: string;
  title: string | null;
  type: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface ChatEventDTO {
  id?: number;
  batchId?: number;
  eventOrder?: number;
  type: string;
  content: string | null;
  toolName: string | null;
  toolCallId: string | null;
  toolArguments: string | null;
  contextPromptTokens?: number | null;
  contextLength?: number | null;
}

export interface ChatAttachmentDTO {
  name: string;
  url: string;
  mimeType: string;
}

export interface ChatEventBatchDTO {
  id: number;
  conversationId: number;
  userContent: string;
  attachments: ChatAttachmentDTO[] | null;
  type: "USER" | "CONTEXT_COMPACTION";
  status: string;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  cachedTokens: number | null;
  outputTokensPerSecond: number | null;
  createdAt: number;
  completedAt: number | null;
  events: ChatEventDTO[];
}
