import type { UserDTO } from "@/types";
import { request, uploadRequest } from "./request";

export async function updateProfile(data: { username?: string; displayName?: string }): Promise<UserDTO> {
  const res = await request<UserDTO>("/api/user/profile", {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return res.data;
}

export async function changePassword(oldPassword: string, newPassword: string) {
  return request("/api/user/password", {
    method: "PUT",
    body: JSON.stringify({ oldPassword, newPassword }),
  });
}

export async function uploadAvatar(file: File): Promise<string> {
  return uploadRequest("/api/user/avatar", file);
}
