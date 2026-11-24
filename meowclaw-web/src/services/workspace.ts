import type {
  ConflictStrategy,
  CreateEntryType,
  FileContent,
  FileEntry,
  FileOperationResult,
  WorkspaceDownloadTicket,
  WorkspaceProperties,
  WorkspaceUploadCompleteResult,
  WorkspaceUploadSession,
} from "@/types";
import type { ApiResult } from "@/types";
import { downloadRequest, getToken, request, uploadRequest } from "./request";

export async function listWorkspaceFiles(agentId: number, path = ""): Promise<FileEntry[]> {
  const res = await request<FileEntry[]>(`/api/workspace?agentId=${agentId}&path=${encodeURIComponent(path)}`);
  return res.data;
}

export async function readWorkspaceFile(agentId: number, path: string): Promise<FileContent> {
  const res = await request<FileContent>(`/api/workspace/file?agentId=${agentId}&path=${encodeURIComponent(path)}`);
  return res.data;
}

export async function downloadWorkspaceFile(agentId: number, path: string): Promise<Blob> {
  return downloadRequest(`/api/workspace/download?agentId=${agentId}&path=${encodeURIComponent(path)}`);
}

export async function saveWorkspaceFile(data: {
  agentId: number;
  path: string;
  content: string;
}): Promise<void> {
  await request("/api/workspace/file", {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function moveWorkspaceFile(data: {
  agentId: number;
  fromPath: string;
  toPath: string;
}): Promise<void> {
  await request("/api/workspace/move", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function createWorkspaceEntry(data: {
  agentId: number;
  path: string;
  type: CreateEntryType;
}): Promise<void> {
  await request("/api/workspace/create", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function deleteWorkspaceFile(agentId: number, path: string): Promise<void> {
  await request(`/api/workspace?agentId=${agentId}&path=${encodeURIComponent(path)}`, {
    method: "DELETE",
  });
}

export async function uploadWorkspaceFile(agentId: number, dir: string, file: File): Promise<FileEntry> {
  return uploadRequest<FileEntry>(
    `/api/workspace/upload?agentId=${agentId}&path=${encodeURIComponent(dir)}`,
    file,
  );
}

export async function copyWorkspaceEntries(data: {
  agentId: number;
  sourcePaths: string[];
  targetDir: string;
  conflict: ConflictStrategy;
}): Promise<FileOperationResult> {
  const res = await request<FileOperationResult>("/api/workspace/copy", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function moveWorkspaceEntries(data: {
  agentId: number;
  sourcePaths: string[];
  targetDir: string;
  conflict: ConflictStrategy;
}): Promise<FileOperationResult> {
  const res = await request<FileOperationResult>("/api/workspace/move-batch", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function renameWorkspaceEntry(data: {
  agentId: number;
  path: string;
  newName: string;
  conflict: ConflictStrategy;
}): Promise<FileOperationResult> {
  const res = await request<FileOperationResult>("/api/workspace/rename", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteWorkspaceEntries(agentId: number, paths: string[]): Promise<FileOperationResult> {
  const res = await request<FileOperationResult>("/api/workspace/delete-batch", {
    method: "POST",
    body: JSON.stringify({ agentId, paths }),
  });
  return res.data;
}

export async function getWorkspaceProperties(agentId: number, paths: string[]): Promise<WorkspaceProperties> {
  const res = await request<WorkspaceProperties>("/api/workspace/properties", {
    method: "POST",
    body: JSON.stringify({ agentId, paths }),
  });
  return res.data;
}

export async function prepareWorkspaceDownload(agentId: number, paths: string[]): Promise<WorkspaceDownloadTicket> {
  const res = await request<WorkspaceDownloadTicket>("/api/workspace/download/prepare", {
    method: "POST",
    body: JSON.stringify({ agentId, paths }),
  });
  return res.data;
}

export async function createWorkspaceUploadSession(data: {
  agentId: number;
  targetDir: string;
  fileName: string;
  fileSize: number;
  chunkSize: number;
  totalChunks: number;
}): Promise<WorkspaceUploadSession> {
  const res = await request<WorkspaceUploadSession>("/api/workspace/upload/session", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function getWorkspaceUploadSession(uploadId: string): Promise<WorkspaceUploadSession> {
  const res = await request<WorkspaceUploadSession>(`/api/workspace/upload/session/${encodeURIComponent(uploadId)}`);
  return res.data;
}

export async function uploadWorkspaceChunk(
  uploadId: string,
  index: number,
  chunk: Blob,
  signal?: AbortSignal,
): Promise<WorkspaceUploadSession> {
  const headers: Record<string, string> = { "Content-Type": "application/octet-stream" };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(
    `/api/workspace/upload/session/${encodeURIComponent(uploadId)}/chunk/${index}`,
    { method: "PUT", headers, body: chunk, signal },
  );
  const json = await res.json().catch(() => null) as ApiResult<WorkspaceUploadSession> | null;
  if (!json) throw new Error(`上传分块失败（HTTP ${res.status}）`);
  if (json.code !== "0") throw new Error(json.message);
  return json.data;
}

export async function completeWorkspaceUpload(
  uploadId: string,
  conflict: ConflictStrategy,
): Promise<WorkspaceUploadCompleteResult> {
  const res = await request<WorkspaceUploadCompleteResult>(
    `/api/workspace/upload/session/${encodeURIComponent(uploadId)}/complete`,
    { method: "POST", body: JSON.stringify({ conflict }) },
  );
  return res.data;
}

export async function cancelWorkspaceUpload(uploadId: string): Promise<void> {
  await request(`/api/workspace/upload/session/${encodeURIComponent(uploadId)}`, { method: "DELETE" });
}
