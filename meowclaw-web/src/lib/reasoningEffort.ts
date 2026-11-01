export const BUILTIN_REASONING_EFFORTS = ["none", "minimal", "low", "medium", "high", "xhigh", "max"] as const;

const REASONING_EFFORT_PRIORITY = [...BUILTIN_REASONING_EFFORTS].reverse();

export function parseReasoningEffortOptions(raw: string | null | undefined): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return [...new Set(parsed.filter((value): value is string => typeof value === "string" && value.trim().length > 0).map((value) => value.trim()))];
  } catch {
    return [];
  }
}

export function normalizeReasoningEffortOptions(raw: string): string[] {
  const parsed = JSON.parse(raw) as unknown;
  if (!Array.isArray(parsed)) {
    throw new Error("思考强度可选项必须是JSON字符串数组");
  }
  if (!parsed.every((value) => typeof value === "string" && value.trim().length > 0)) {
    throw new Error("思考强度可选项必须全部是非空字符串");
  }
  return parseReasoningEffortOptions(JSON.stringify(parsed));
}

export function defaultReasoningEffort(options: string[]): string | null {
  if (options.length === 0) return null;
  if (options.includes("medium")) return "medium";
  for (const builtin of REASONING_EFFORT_PRIORITY) {
    if (options.includes(builtin)) return builtin;
  }
  return options[options.length - 1];
}
