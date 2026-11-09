import type { EnvironmentVariableDTO } from "@/types";
import { request } from "./request";

export async function listEnvironmentVariables(agentId?: number): Promise<EnvironmentVariableDTO[]> {
  const url = agentId == null
    ? "/api/environment-variable"
    : `/api/environment-variable?agentId=${agentId}`;
  const res = await request<EnvironmentVariableDTO[]>(url);
  return res.data;
}

export async function createEnvironmentVariable(data: {
  agentId: number | null;
  key: string;
  value: string;
}): Promise<EnvironmentVariableDTO> {
  const res = await request<EnvironmentVariableDTO>("/api/environment-variable", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateEnvironmentVariable(
  id: number,
  data: {
    key: string;
    value: string;
  },
): Promise<EnvironmentVariableDTO> {
  const res = await request<EnvironmentVariableDTO>(`/api/environment-variable/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteEnvironmentVariable(id: number) {
  return request(`/api/environment-variable/${id}`, { method: "DELETE" });
}
