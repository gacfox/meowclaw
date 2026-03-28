import { request } from "@/services/request";

export interface LlmConfigDto {
  id?: number;
  name: string;
  apiUrl: string;
  apiKey: string;
  model: string;
  maxContextLength: number;
  temperature?: number;
}

export const llmService = {
  async list() {
    return request.request<LlmConfigDto[]>("/api/llms");
  },

  async getById(id: number) {
    return request.request<LlmConfigDto>(`/api/llms/${id}`);
  },

  async create(data: LlmConfigDto) {
    return request.request<LlmConfigDto>("/api/llms", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },

  async update(id: number, data: LlmConfigDto) {
    return request.request<LlmConfigDto>(`/api/llms/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/llms/${id}`, {
      method: "DELETE",
    });
  },

  async test(data: LlmConfigDto) {
    return request.request<boolean>("/api/llms/test", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },
};
