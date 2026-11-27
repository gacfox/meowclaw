export interface LlmDTO {
  id: number;
  name: string;
  endpointUrl: string;
  sk: string | null;
  model: string;
  maxTokens: number | null;
  contextLength: number | null;
  temperature: number | null;
  capabilities: string | null;
  reasoningEffortOptions: string | null;
  createdAt: number;
  updatedAt: number;
}

export interface EmbeddingModelDTO {
  id: number;
  name: string;
  endpointUrl: string;
  sk: string | null;
  model: string;
  dimensions: number;
  createdAt: number;
  updatedAt: number;
}

export interface EmbeddingModelTestResultDTO {
  success: boolean;
  dimensions: number | null;
  errorMessage: string | null;
}

export interface ToolInfoDTO {
  name: string;
  description: string;
}
