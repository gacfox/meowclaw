import type { EmbeddingModelDTO, EmbeddingModelTestResultDTO } from "@/types";
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

export async function testEmbeddingModel(data: {
  endpointUrl: string;
  sk?: string;
  model: string;
  dimensions: number;
}): Promise<EmbeddingModelTestResultDTO> {
  const res = await request<EmbeddingModelTestResultDTO>("/api/embedding-model/test", {
    method: "POST",
    body: JSON.stringify(data),
  });
  return res.data;
}
