export interface SkillPackageDTO {
  id: number;
  name: string;
  description: string | null;
  storedFilename: string;
  originalFilename: string;
  fileSize: number;
  createdAt: number;
  updatedAt: number;
}

export type SkillInstallStatus = "INSTALLED" | "CONFLICT";

export interface SkillInstallRequest {
  agentId: number;
  overwrite?: boolean;
}

export interface SkillInstallResultDTO {
  status: SkillInstallStatus;
  existingFiles: string[];
}
