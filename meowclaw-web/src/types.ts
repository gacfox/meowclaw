export interface ApiResult<T = unknown> {
  code: string;
  message: string;
  data: T;
}

export interface UserDTO {
  id: number;
  username: string;
  displayName: string;
  avatarUrl: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface LlmDTO {
  id: number;
  name: string;
  endpointUrl: string;
  sk: string | null;
  model: string;
  maxTokens: number | null;
  contextLength: number | null;
  temperature: number | null;
  capabilities: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface EmbeddingModelDTO {
  id: number;
  name: string;
  endpointUrl: string;
  sk: string | null;
  model: string;
  dimensions: number;
  createdAt: number;
  updatedAt: number;
}

export interface ToolInfoDTO {
  name: string;
  description: string;
}

export interface AgentDTO {
  id: number;
  name: string;
  avatarUrl: string | null;
  persona: string | null;
  enabledTools: string | null;
  enabledMcpTools: string | null;
  llmId: number | null;
  secondaryLlmId: number | null;
  workspaceFolder: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface ConversationDTO {
  id: number;
  agentId: number;
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
}

export interface ChatEventBatchDTO {
  id: number;
  conversationId: number;
  userContent: string;
  type: "USER" | "CONTEXT_COMPACTION";
  status: string;
  errorMessage: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  createdAt: number;
  completedAt: number | null;
  events: ChatEventDTO[];
}

export interface PageResult<T> {
  list: T[];
  total: number;
  current: number;
  pageSize: number;
}

export interface ScheduledTaskDTO {
  id: number;
  name: string;
  agentId: number;
  userPrompt: string;
  cronExpression: string;
  createNewSession: boolean;
  enabled: boolean;
  lastStatus: string | null;
  lastExecutedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface ScheduledTaskExecutionDTO {
  id: number;
  scheduledTaskId: number;
  conversationId: number;
  status: string;
  executedAt: number;
}

export type McpProtocol = "STDIO" | "STREAMABLE_HTTP" | "SSE";
export type McpStatus = "CONNECTED" | "DISCONNECTED" | "ERROR";

export interface McpToolDTO {
  name: string;
  serviceName: string;
  description: string;
}

export interface McpToolInfo {
  name: string;
  description: string | null;
  inputSchema: Record<string, unknown> | null;
}

export interface McpServiceDTO {
  id: number;
  name: string;
  description: string | null;
  protocol: McpProtocol;
  config: string;
  enabled: boolean;
  status: McpStatus;
  tools: McpToolDTO[];
  errorMessage: string | null;
  lastCheckedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface McpTestResultDTO {
  success: boolean;
  errorMessage: string | null;
  tools: McpToolInfo[];
}

export interface SkillPackageDTO {
  id: number;
  name: string;
  description: string | null;
  storedFilename: string;
  originalFilename: string;
  fileSize: number;
  createdAt: number;
  updatedAt: number;
}

export type SkillInstallStatus = "INSTALLED" | "CONFLICT";

export interface SkillInstallRequest {
  agentId: number;
  overwrite?: boolean;
}

export interface SkillInstallResultDTO {
  status: SkillInstallStatus;
  existingFiles: string[];
}

export interface TokenStatsSummary {
  totalInputTokens: number;
  totalOutputTokens: number;
  totalTokens: number;
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

export type FileKind = "TEXT" | "IMAGE" | "UNSUPPORTED";
export type CreateEntryType = "FILE" | "DIR";

export interface FileEntry {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  lastModified: number;
}

export interface FileContent {
  kind: FileKind;
  mimeType: string | null;
  content: string | null;
  dataUrl: string | null;
}
