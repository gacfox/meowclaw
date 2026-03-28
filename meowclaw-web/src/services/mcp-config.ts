import { request } from "@/services/request";

export interface McpConfigDto {
  id?: number;
  name: string;
  transportType: string;
  command?: string;
  args?: string;
  envVars?: string;
  url?: string;
}

export interface McpClientStatusDto {
  name: string;
  status: "INITIALIZING" | "CONNECTED" | "FAILED";
  statusLabel: string;
  errorMessage?: string;
}

export const mcpConfigService = {
  async list() {
    return request.request<McpConfigDto[]>("/api/mcp-configs");
  },

  async getStatus() {
    return request.request<McpClientStatusDto[]>("/api/mcp-configs/status");
  },

  async reinitialize(id: number) {
    return request.request<McpClientStatusDto>(
      `/api/mcp-configs/${id}/reinitialize`,
      {
        method: "POST",
      },
    );
  },

  async getById(id: number) {
    return request.request<McpConfigDto>(`/api/mcp-configs/${id}`);
  },

  async create(data: McpConfigDto) {
    return request.request<McpConfigDto>("/api/mcp-configs", {
      method: "POST",
      body: JSON.stringify(data),
      headers: {
        "Content-Type": "application/json",
      },
    });
  },

  async update(id: number, data: McpConfigDto) {
    return request.request<McpConfigDto>(`/api/mcp-configs/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
      headers: {
        "Content-Type": "application/json",
      },
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/mcp-configs/${id}`, {
      method: "DELETE",
    });
  },
};
