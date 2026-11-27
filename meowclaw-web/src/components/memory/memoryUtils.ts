export const MEMORY_GRAPH_COLORS = {
  light: { fact: "#4f83cc", preference: "#1f7a4d", rule: "#c28c3c", entity: "#b34d8b" },
  dark: { fact: "#5594d0", preference: "#35a773", rule: "#b88930", entity: "#c05a97" },
} as const;

const TYPE_LABELS: Record<string, string> = {
  fact: "事实",
  preference: "偏好",
  rule: "规则",
};

export function memoryTypeBadgeVariant(type: string): "default" | "secondary" | "outline" {
  if (type === "preference") return "secondary";
  if (type === "rule") return "outline";
  return "default";
}

export function memoryTypeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

export function formatMemoryTimestamp(ts: number | null): string {
  if (ts == null || ts <= 0) return "-";
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function truncateMemoryLabel(text: string, max: number): string {
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > max ? flat.slice(0, max) + "…" : flat;
}
