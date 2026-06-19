import type {
  McpServiceDTO,
  McpTestResultDTO,
  McpToolDTO,
  McpProtocol,
} from "@/types";
import { request } from "./request";

export async function listMcpServices(): Promise<McpServiceDTO[]> {
  const res = await request<McpServiceDTO[]>("/api/mcp-service");
  return res.data;
}

export async function createMcpService(data: {
  name: string;
  description?: string;
  protocol: McpProtocol;
  config: string;
}): Promise<McpServiceDTO> {
  const res = await request<McpServiceDTO>("/api/mcp-service", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateMcpService(
  id: number,
  data: {
    name?: string;
    description?: string;
    protocol?: McpProtocol;
    config?: string;
  }
): Promise<McpServiceDTO> {
  const res = await request<McpServiceDTO>(`/api/mcp-service/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteMcpService(id: number) {
  return request(`/api/mcp-service/${id}`, { method: "DELETE" });
}

export async function toggleMcpService(id: number): Promise<McpServiceDTO> {
  const res = await request<McpServiceDTO>(`/api/mcp-service/${id}/toggle`, {
    method: "PUT",
  });
  return res.data;
}

export async function refreshMcpService(id: number): Promise<McpServiceDTO> {
  const res = await request<McpServiceDTO>(`/api/mcp-service/${id}/refresh`, {
    method: "POST",
  });
  return res.data;
}

export async function listMcpTools(): Promise<McpToolDTO[]> {
  const res = await request<McpToolDTO[]>("/api/mcp-service/tool");
  return res.data;
}

export async function testMcpService(data: {
  protocol: McpProtocol;
  config: string;
}): Promise<McpTestResultDTO> {
  const res = await request<McpTestResultDTO>("/api/mcp-service/test", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}
