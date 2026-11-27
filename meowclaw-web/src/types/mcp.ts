export type McpProtocol = "STDIO" | "STREAMABLE_HTTP" | "SSE";
export type McpStatus = "CONNECTED" | "DISCONNECTED" | "ERROR";

export interface McpToolDTO {
  name: string;
  serviceName: string;
  description: string;
}

export interface McpToolInfo {
  name: string;
  description: string | null;
  inputSchema: Record<string, unknown> | null;
}

export interface McpServiceDTO {
  id: number;
  name: string;
  description: string | null;
  protocol: McpProtocol;
  config: string;
  enabled: boolean;
  status: McpStatus;
  tools: McpToolDTO[];
  errorMessage: string | null;
  lastCheckedAt: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface McpTestResultDTO {
  success: boolean;
  errorMessage: string | null;
  tools: McpToolInfo[];
}
