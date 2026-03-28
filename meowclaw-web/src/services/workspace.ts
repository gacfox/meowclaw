import { request } from "@/services/request";

export interface WorkspaceEntryDto {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  modifiedAt: number;
  mime?: string | null;
}

export interface WorkspacePreviewDto {
  type: "text" | "binary";
  mime?: string | null;
  content?: string;
  size: number;
}

export const workspaceService = {
  async list(agentId: number, path?: string) {
    const params = path ? `?path=${encodeURIComponent(path)}` : "";
    return request.request<WorkspaceEntryDto[]>(
      `/api/workspaces/agent/${agentId}/list${params}`,
    );
  },

  async preview(agentId: number, path: string) {
    return request.request<WorkspacePreviewDto>(
      `/api/workspaces/agent/${agentId}/preview?path=${encodeURIComponent(path)}`,
    );
  },

  async delete(agentId: number, path: string) {
    return request.request<void>(
      `/api/workspaces/agent/${agentId}?path=${encodeURIComponent(path)}`,
      { method: "DELETE" },
    );
  },

  async move(agentId: number, from: string, to: string) {
    return request.request<void>(`/api/workspaces/agent/${agentId}/move`, {
      method: "POST",
      body: JSON.stringify({ from, to }),
    });
  },

  async download(agentId: number, path: string) {
    const token = request.getToken();
    const headers: Record<string, string> = {};
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }
    const response = await fetch(
      `/api/workspaces/agent/${agentId}/download?path=${encodeURIComponent(path)}`,
      { headers },
    );
    return response;
  },
};
