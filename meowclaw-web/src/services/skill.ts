import { request } from "@/services/request";
import { useAuthStore } from "@/stores/authStore";

export interface SkillDto {
  id?: number;
  name: string;
  description?: string;
  createdAt?: number;
}

export const skillService = {
  async list() {
    return request.request<SkillDto[]>("/api/skills");
  },

  async upload(data: { name: string; description?: string; file: File }) {
    const formData = new FormData();
    formData.append("name", data.name);
    if (data.description) {
      formData.append("description", data.description);
    }
    formData.append("file", data.file);
    return request.request<SkillDto>("/api/skills", {
      method: "POST",
      body: formData,
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/skills/${id}`,
      {
        method: "DELETE",
      },
    );
  },

  async update(id: number, data: { name: string; description?: string }) {
    return request.request<SkillDto>(`/api/skills/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async download(id: number, filename: string) {
    const token = useAuthStore.getState().token;
    const response = await fetch(`/api/skills/${id}/download`, {
      headers: token ? { Authorization: `Bearer ${token}` } : undefined,
    });
    if (!response.ok) {
      throw new Error(`下载失败 (${response.status})`);
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    window.URL.revokeObjectURL(url);
  },

  async preview(id: number) {
    return request.request<string>(`/api/skills/${id}/preview`);
  },
};
