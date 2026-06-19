import type { ApiResult } from "@/types";

const TOKEN_KEY = "meowclaw_token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function request<T>(url: string, options?: RequestInit): Promise<ApiResult<T>> {
  const token = getToken();
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(url, {
    headers,
    ...options,
  });
  const json: ApiResult<T> = await res.json();
  if (json.code !== "0") {
    throw new Error(json.message);
  }
  return json;
}

export async function uploadRequest<T = string>(url: string, file: File): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const formData = new FormData();
  formData.append("file", file);
  const res = await fetch(url, {
    method: "POST",
    headers,
    body: formData,
  });
  const json: ApiResult<T> = await res.json();
  if (json.code !== "0") {
    throw new Error(json.message);
  }
  return json.data;
}
