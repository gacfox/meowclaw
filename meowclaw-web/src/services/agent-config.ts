import { request } from "@/services/request";

export interface AgentConfigDto {
  id?: number;
  name: string;
  avatar?: string;
  systemPrompt?: string;
  enabledTools?: string;
  enabledMcpTools?: string;
  defaultLlmId: number;
  workspaceFolder?: string;
}

export const agentConfigService = {
  async list() {
    return request.request<AgentConfigDto[]>("/api/agent-configs");
  },

  async getById(id: number) {
    return request.request<AgentConfigDto>(`/api/agent-configs/${id}`);
  },

  async create(data: AgentConfigDto, avatar?: File) {
    const formData = new FormData();
    formData.append("data", JSON.stringify(data));
    if (avatar) {
      formData.append("avatar", avatar);
    }

    return request.request<AgentConfigDto>("/api/agent-configs", {
      method: "POST",
      body: formData,
    });
  },

  async update(id: number, data: AgentConfigDto, avatar?: File) {
    const formData = new FormData();
    formData.append("data", JSON.stringify(data));
    if (avatar) {
      formData.append("avatar", avatar);
    }

    return request.request<AgentConfigDto>(`/api/agent-configs/${id}`, {
      method: "PUT",
      body: formData,
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/agent-configs/${id}`, {
      method: "DELETE",
    });
  },
};
