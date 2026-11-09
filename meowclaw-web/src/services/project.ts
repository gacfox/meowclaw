import type { ProjectDTO } from "@/types";
import { request } from "./request";

export async function createProject(agentId: number, name: string, path?: string): Promise<ProjectDTO> {
  const res = await request<ProjectDTO>("/api/project", {
    method: "POST",
    body: JSON.stringify({ agentId, name, path }),
  });
  return res.data;
}

export async function updateProject(id: number, updates: { name?: string; path?: string }): Promise<ProjectDTO> {
  const res = await request<ProjectDTO>(`/api/project/${id}`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
  return res.data;
}

export async function renameProject(id: number, name: string): Promise<ProjectDTO> {
  return updateProject(id, { name });
}

export async function deleteProject(id: number) {
  return request(`/api/project/${id}`, { method: "DELETE" });
}
