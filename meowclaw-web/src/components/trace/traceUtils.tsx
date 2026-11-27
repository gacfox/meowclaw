import { Badge } from "@/components/ui/badge";

export function traceStatusBadge(status: string) {
  if (status === "RUNNING") return <Badge variant="secondary">运行中</Badge>;
  if (status === "ERROR") return <Badge variant="destructive">错误</Badge>;
  return <Badge variant="default">已完成</Badge>;
}

export function formatTraceTimestamp(ts: number | null): string {
  if (ts == null) return "-";
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function formatTraceDuration(batch: { createdAt: number; completedAt: number | null }): string {
  if (batch.completedAt == null) return "-";
  return `${((batch.completedAt - batch.createdAt) / 1000).toFixed(1)}s`;
}
