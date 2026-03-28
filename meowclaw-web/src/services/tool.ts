import { request } from "@/services/request";

export interface ToolDto {
  id: string;
  name: string;
  description?: string;
}

export const toolService = {
  async list() {
    return request.request<ToolDto[]>("/api/tools");
  },
};

