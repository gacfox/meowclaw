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

export class HttpRequestError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "HttpRequestError";
    this.status = status;
  }
}

export async function resourceRequest(url: string, options?: RequestInit): Promise<Response> {
  const token = getToken();
  const headers = new Headers(options?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const res = await fetch(url, { ...options, headers });
  if (!res.ok) {
    const error = (await res.json().catch(() => null)) as ApiResult<unknown> | null;
    throw new HttpRequestError(error?.message || `请求失败（HTTP ${res.status}）`, res.status);
  }
  return res;
}

export async function downloadRequest(url: string, signal?: AbortSignal): Promise<Blob> {
  const res = await resourceRequest(url, { signal });
  return res.blob();
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
