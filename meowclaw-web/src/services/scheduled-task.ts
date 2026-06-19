import type { ScheduledTaskDTO, ScheduledTaskExecutionDTO } from "@/types";
import { request } from "./request";

export async function listScheduledTasks(): Promise<ScheduledTaskDTO[]> {
  const res = await request<ScheduledTaskDTO[]>("/api/scheduled-task");
  return res.data;
}

export async function createScheduledTask(data: {
  name: string;
  agentId: number;
  userPrompt: string;
  cronExpression: string;
  createNewSession?: boolean;
}): Promise<ScheduledTaskDTO> {
  const res = await request<ScheduledTaskDTO>("/api/scheduled-task", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateScheduledTask(
  id: number,
  data: {
    name?: string;
    agentId?: number;
    userPrompt?: string;
    cronExpression?: string;
    createNewSession?: boolean;
  }
): Promise<ScheduledTaskDTO> {
  const res = await request<ScheduledTaskDTO>(`/api/scheduled-task/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteScheduledTask(id: number) {
  return request(`/api/scheduled-task/${id}`, { method: "DELETE" });
}

export async function toggleScheduledTask(id: number): Promise<ScheduledTaskDTO> {
  const res = await request<ScheduledTaskDTO>(`/api/scheduled-task/${id}/toggle`, {
    method: "PUT",
  });
  return res.data;
}

export async function triggerScheduledTask(id: number) {
  return request(`/api/scheduled-task/${id}/trigger`, { method: "POST" });
}

export async function listScheduledTaskExecutions(taskId: number): Promise<ScheduledTaskExecutionDTO[]> {
  const res = await request<ScheduledTaskExecutionDTO[]>(`/api/scheduled-task/${taskId}/execution`);
  return res.data;
}
