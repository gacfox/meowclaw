import type { TokenStatsDTO } from "@/types";
import { request } from "./request";

export async function getTokenStats(params: {
  start: number;
  end: number;
  llmId?: number;
}): Promise<TokenStatsDTO> {
  const query = new URLSearchParams({
    start: String(params.start),
    end: String(params.end),
  });
  if (params.llmId != null) query.set("llmId", String(params.llmId));
  const res = await request<TokenStatsDTO>(`/api/tokens/stats?${query.toString()}`);
  return res.data;
}
