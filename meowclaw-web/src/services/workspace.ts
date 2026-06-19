import type { CreateEntryType, FileContent, FileEntry } from "@/types";
import { request, uploadRequest } from "./request";

export async function listWorkspaceFiles(agentId: number, path = ""): Promise<FileEntry[]> {
  const res = await request<FileEntry[]>(`/api/workspace?agentId=${agentId}&path=${encodeURIComponent(path)}`);
  return res.data;
}

export async function readWorkspaceFile(agentId: number, path: string): Promise<FileContent> {
  const res = await request<FileContent>(`/api/workspace/file?agentId=${agentId}&path=${encodeURIComponent(path)}`);
  return res.data;
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
