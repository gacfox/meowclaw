export interface SubagentContent {
  childConversationId: number;
  description?: string;
  status: string;
}

export function parseSubagentContent(content: string | null | undefined): SubagentContent | null {
  if (!content) return null;
  try {
    const obj = JSON.parse(content);
    if (obj && typeof obj.status === "string") return obj as SubagentContent;
    return null;
  } catch {
    return null;
  }
}
