import { request } from "./request";

export async function echo(): Promise<{ timestamp: number }> {
  const res = await request<{ timestamp: number }>("/api/health/echo");
  return res.data;
}
