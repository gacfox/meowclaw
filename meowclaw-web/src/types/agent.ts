export interface AgentDTO {
  id: number;
  name: string;
  avatarUrl: string | null;
  persona: string | null;
  enabledTools: string | null;
  enabledMcpTools: string | null;
  llmId: number | null;
  secondaryLlmId: number | null;
  embeddingModelId: number | null;
  workspaceFolder: string | null;
  aggressiveMemoryRecall: boolean | null;
  allowAsSubagent: boolean;
  subagentDescription: string | null;
  professionalSubagentIds: number[];
  createdAt: number;
  updatedAt: number;
}

export interface ConfigImportCategoryResult {
  type: string;
  totalCount: number;
  importedCount: number;
  skippedCount: number;
  failedCount: number;
  renamedCount: number;
}

export interface ConfigImportItemResult {
  type: string;
  sourceId: number | null;
  originalName: string | null;
  finalName: string | null;
  newId: number | null;
  status: "IMPORTED" | "SKIPPED" | "FAILED";
  message: string | null;
}

export interface ConfigImportResult {
  totalCount: number;
  importedCount: number;
  skippedCount: number;
  failedCount: number;
  renamedCount: number;
  categories: ConfigImportCategoryResult[];
  items: ConfigImportItemResult[];
}
