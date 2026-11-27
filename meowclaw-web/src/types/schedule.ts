export interface ScheduledTaskDTO {
  id: number;
  name: string;
  agentId: number;
  userPrompt: string;
  cronExpression: string;
  createNewSession: boolean;
  enabled: boolean;
  lastStatus: string | null;
  lastExecutedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface ScheduledTaskExecutionDTO {
  id: number;
  scheduledTaskId: number;
  conversationId: number;
  status: string;
  executedAt: number;
}
