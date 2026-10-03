import type { ProjectDTO } from "@/types";
import { request } from "./request";

export async function createProject(agentId: number, name: string): Promise<ProjectDTO> {
  const res = await request<ProjectDTO>("/api/project", {
    method: "POST",
    body: JSON.stringify({ agentId, name }),
  });
  return res.data;
}

export async function renameProject(id: number, name: string): Promise<ProjectDTO> {
  const res = await request<ProjectDTO>(`/api/project/${id}`, {
    method: "PUT",
    body: JSON.stringify({ name }),
  });
  return res.data;
}

export async function deleteProject(id: number) {
  return request(`/api/project/${id}`, { method: "DELETE" });
}
