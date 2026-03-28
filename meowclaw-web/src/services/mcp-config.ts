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

export const mcpConfigService = {
  async list() {
    return request.request<McpConfigDto[]>("/api/mcp-configs");
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
