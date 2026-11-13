import { request } from "./request";

export const SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT = "meowclaw:system-resource-monitor-changed";

export interface SystemResourceMetrics {
  cpuUsage: number | null;
  memoryUsage: number | null;
  timestamp: number;
}

export async function getSystemResourceMetrics(): Promise<SystemResourceMetrics> {
  const res = await request<SystemResourceMetrics>("/api/system-resource/metrics");
  return res.data;
}

export async function getSystemResourceMonitorEnabled(): Promise<boolean> {
  const res = await request<boolean>("/api/system-resource/enabled");
  return res.data;
}

export async function updateSystemResourceMonitorEnabled(enabled: boolean): Promise<boolean> {
  const res = await request<boolean>("/api/system-resource/enabled", {
    method: "PUT",
    body: JSON.stringify({ enabled }),
  });
  return res.data;
}
