import { request } from "@/services/request";

export interface ScheduledTaskDto {
  id?: number;
  name: string;
  agentConfigId: number;
  userPrompt: string;
  cronExpression: string;
  newSessionEach: boolean;
  boundConversationId?: number;
  enabled: boolean;
  lastExecutedAt?: number;
  agentName?: string;
}

export interface PageDto<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export const scheduledTaskService = {
  async list(page = 1, pageSize = 20) {
    return request.request<PageDto<ScheduledTaskDto>>(
      `/api/scheduled-tasks?page=${page}&pageSize=${pageSize}`,
    );
  },

  async getById(id: number) {
    return request.request<ScheduledTaskDto>(`/api/scheduled-tasks/${id}`);
  },

  async create(data: ScheduledTaskDto) {
    return request.request<ScheduledTaskDto>("/api/scheduled-tasks", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },

  async update(id: number, data: ScheduledTaskDto) {
    return request.request<ScheduledTaskDto>(`/api/scheduled-tasks/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/scheduled-tasks/${id}`, {
      method: "DELETE",
    });
  },

  async toggleEnabled(id: number) {
    return request.request<ScheduledTaskDto>(
      `/api/scheduled-tasks/${id}/toggle`,
      {
        method: "POST",
      },
    );
  },

  async trigger(id: number) {
    return request.request<void>(`/api/scheduled-tasks/${id}/trigger`, {
      method: "POST",
    });
  },

  async getNextExecution(cronExpression: string) {
    return request.request<string>(
      `/api/scheduled-tasks/next-execution?cronExpression=${encodeURIComponent(cronExpression)}`,
    );
  },
};
