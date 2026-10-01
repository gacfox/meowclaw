export interface ApprovalContent {
  decision: string;
  policyName?: string;
  matchedRuleIndex?: number;
  decidedAt?: number | null;
}

export function parseApprovalContent(content: string | null | undefined): ApprovalContent | null {
  if (!content) return null;
  try {
    const obj = JSON.parse(content);
    if (obj && typeof obj.decision === "string") return obj as ApprovalContent;
    return null;
  } catch {
    return null;
  }
}
