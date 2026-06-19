import type { UserDTO } from "@/types";
import { request, setToken, removeToken } from "./request";

export async function checkInit(): Promise<boolean> {
  const res = await request<boolean>("/api/auth/check-init");
  return res.data;
}

export async function initSystem(username: string, password: string) {
  return request("/api/auth/init", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export async function login(username: string, password: string): Promise<UserDTO> {
  const res = await request<{ user: UserDTO; token: string }>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
  setToken(res.data.token);
  return res.data.user;
}

export async function logout() {
  removeToken();
}

export async function getCurrentUser(): Promise<UserDTO> {
  const res = await request<UserDTO>("/api/auth/me");
  return res.data;
}
