import type { ToolInfoDTO } from "@/types";
import { request } from "./request";

export async function listTools(): Promise<ToolInfoDTO[]> {
  const res = await request<ToolInfoDTO[]>("/api/tool");
  return res.data;
}
