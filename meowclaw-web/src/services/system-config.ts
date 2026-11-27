import type { ConfigImportResult } from "@/types";
import { downloadRequest, uploadRequest } from "./request";

export async function exportAgentConfig(): Promise<Blob> {
  return downloadRequest("/api/system-config/agent-config/export");
}

export async function importAgentConfig(file: File): Promise<ConfigImportResult> {
  return uploadRequest<ConfigImportResult>("/api/system-config/agent-config/import", file);
}
