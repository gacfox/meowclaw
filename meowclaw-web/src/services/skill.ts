import type { SkillInstallRequest, SkillInstallResultDTO, SkillPackageDTO } from "@/types";
import { request, uploadRequest } from "./request";

export async function listSkills(): Promise<SkillPackageDTO[]> {
  const res = await request<SkillPackageDTO[]>("/api/skill");
  return res.data;
}

export async function uploadSkill(file: File): Promise<SkillPackageDTO> {
  return uploadRequest<SkillPackageDTO>("/api/skill", file);
}

export async function deleteSkill(id: number) {
  return request(`/api/skill/${id}`, { method: "DELETE" });
}

export async function installSkill(id: number, body: SkillInstallRequest): Promise<SkillInstallResultDTO> {
  const res = await request<SkillInstallResultDTO>(`/api/skill/${id}/install`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return res.data;
}
