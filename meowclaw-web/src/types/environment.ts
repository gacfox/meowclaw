export interface EnvironmentVariableDTO {
  id: number;
  agentId: number | null;
  key: string;
  value: string;
  createdAt: number;
  updatedAt: number;
}
