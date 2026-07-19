import type { EmbeddingModelDTO } from "@/types";
import { request } from "./request";

export async function listEmbeddingModels(): Promise<EmbeddingModelDTO[]> {
  const res = await request<EmbeddingModelDTO[]>("/api/embedding-model");
  return res.data;
}

export async function createEmbeddingModel(data: {
  name: string;
  endpointUrl: string;
  sk?: string;
  model: string;
  dimensions: number;
}): Promise<EmbeddingModelDTO> {
  const res = await request<EmbeddingModelDTO>("/api/embedding-model", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function updateEmbeddingModel(id: number, data: {
  name: string;
  endpointUrl: string;
  sk?: string;
  model: string;
  dimensions: number;
}): Promise<EmbeddingModelDTO> {
  const res = await request<EmbeddingModelDTO>(`/api/embedding-model/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function deleteEmbeddingModel(id: number) {
  return request(`/api/embedding-model/${id}`, { method: "DELETE" });
}
