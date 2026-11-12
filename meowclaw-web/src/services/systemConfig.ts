import { downloadRequest, uploadRequest } from "./request";

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

export async function exportAgentConfig(): Promise<Blob> {
  return downloadRequest("/api/system-config/agent-config/export");
}

export async function importAgentConfig(file: File): Promise<ConfigImportResult> {
  return uploadRequest<ConfigImportResult>("/api/system-config/agent-config/import", file);
}
