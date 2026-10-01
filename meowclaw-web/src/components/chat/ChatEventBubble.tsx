import type { ChatEventDTO } from "@/types";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { parseApprovalContent } from "@/lib/approval";
import { Check, ChevronRight, CircleStop, Loader2, ShieldCheck, ShieldX, Wrench } from "lucide-react";
function parseToolArgs(json: string | null | undefined): Record<string, unknown> | null {
  if (!json) return null;
  try {
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function formatArgValue(value: unknown): string {
  if (value === null) return "null";
  if (typeof value === "string") return value;
  return JSON.stringify(value);
}

function truncateSingleLine(text: string, maxLen: number): { text: string; truncated: boolean } {
  if (text.length <= maxLen) return { text, truncated: false };
  return { text: text.slice(0, maxLen), truncated: true };
}

export function ToolCallDetails({
  name,
  args,
  result,
  pending,
}: {
  name: string;
  args?: string;
  result?: string;
  pending?: boolean;
}) {
  const parsed = parseToolArgs(args);
  return (
    <details className="group text-xs">
      <summary className="flex cursor-pointer items-center gap-1 text-muted-foreground list-none [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
        <Wrench className="size-3" />
        {name}
        {pending && <Loader2 className="size-3 animate-spin" />}
      </summary>
      <div className="mt-2 space-y-2 rounded border bg-background/50 p-2">
        {parsed && Object.keys(parsed).length > 0 && (
          <Table>
            <TableBody>
              {Object.entries(parsed).map(([key, value]) => {
                const text = formatArgValue(value);
                const truncated = truncateSingleLine(text, 80);
                return (
                  <TableRow key={key}>
                    <TableCell className="w-24 py-1 text-muted-foreground">{key}</TableCell>
                    <TableCell className="py-1" title={truncated.truncated ? text : undefined}>
                      <span className="inline-block max-w-full truncate">{truncated.text}</span>
                      {truncated.truncated && (
                        <span className="ml-1 text-muted-foreground">…</span>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
        <div>
          <div className="mb-1 text-xs text-muted-foreground">执行结果：</div>
          <pre className="whitespace-pre-wrap rounded bg-muted p-2 text-xs">{result ?? (pending ? "执行中..." : "(无结果)")}</pre>
        </div>
      </div>
    </details>
  );
}

export function FinalAnswerIndicator() {
  return (
    <div className="flex items-center gap-1 text-xs text-muted-foreground">
      <Check className="size-3" />
      <span>final_answer</span>
    </div>
  );
}

const APPROVAL_LABELS: Record<string, string> = {
  pending: "等待审批",
  approved: "已批准",
  rejected: "已拒绝",
  interrupted: "已中断",
};

export function ApprovalDetails({
  name,
  args,
  decision,
  policyName,
  matchedRuleIndex,
}: {
  name: string;
  args?: string;
  decision: string;
  policyName?: string;
  matchedRuleIndex?: number;
}) {
  const parsed = parseToolArgs(args);
  return (
    <details className="group text-xs">
      <summary className="flex cursor-pointer items-center gap-1 text-muted-foreground list-none [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
        {decision === "approved" ? (
          <ShieldCheck className="size-3 text-green-500" />
        ) : decision === "rejected" ? (
          <ShieldX className="size-3 text-destructive" />
        ) : decision === "interrupted" ? (
          <CircleStop className="size-3" />
        ) : (
          <Loader2 className="size-3 animate-spin" />
        )}
        审批 · {name}
        <span>{APPROVAL_LABELS[decision] ?? decision}</span>
      </summary>
      <div className="mt-2 space-y-2 rounded border bg-background/50 p-2">
        {policyName && (
          <div className="text-xs text-muted-foreground">
            策略：{policyName} · {matchedRuleIndex != null && matchedRuleIndex >= 0 ? `规则 #${matchedRuleIndex + 1}` : "默认决策"}
          </div>
        )}
        {parsed && Object.keys(parsed).length > 0 && (
          <Table>
            <TableBody>
              {Object.entries(parsed).map(([key, value]) => {
                const text = formatArgValue(value);
                const truncated = truncateSingleLine(text, 80);
                return (
                  <TableRow key={key}>
                    <TableCell className="w-24 py-1 text-muted-foreground">{key}</TableCell>
                    <TableCell className="py-1" title={truncated.truncated ? text : undefined}>
                      <span className="inline-block max-w-full truncate">{truncated.text}</span>
                      {truncated.truncated && <span className="ml-1 text-muted-foreground">…</span>}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </div>
    </details>
  );
}

export function BatchBubble({ events }: { events: ChatEventDTO[] }) {
  // 执行器保证 tool_result 与 tool_call 严格同序同数；部分模型端点返回空 toolCallId，无法按 id 匹配，故按序配对
  const toolResults = events.filter((e) => e.type === "tool_result");
  let resultCursor = 0;
  const finalAnswer = events.find((e) => e.type === "final_answer");

  return (
    <div className="max-w-[80%] space-y-2 rounded-lg bg-muted px-4 py-2 text-sm">
      {events.map((event, i) => {
        if (event.type === "thinking") {
          return (
            <details key={i} className="group text-xs text-muted-foreground">
              <summary className="flex cursor-pointer items-center gap-1 list-none [&::-webkit-details-marker]:hidden">
                <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
                思考过程
              </summary>
              <div className="mt-1 whitespace-pre-wrap">{event.content}</div>
            </details>
          );
        }
        if (event.type === "tool_call") {
          const result = toolResults[resultCursor]?.content ?? undefined;
          resultCursor++;
          if (event.toolName === "final_answer") {
            return <FinalAnswerIndicator key={i} />;
          }
          return (
            <ToolCallDetails
              key={i}
              name={event.toolName ?? "tool"}
              args={event.toolArguments ?? undefined}
              result={result}
            />
          );
        }
        if (event.type === "approval") {
          const info = parseApprovalContent(event.content);
          return (
            <ApprovalDetails
              key={i}
              name={event.toolName ?? "tool"}
              args={event.toolArguments ?? undefined}
              decision={info?.decision ?? "pending"}
              policyName={info?.policyName}
              matchedRuleIndex={info?.matchedRuleIndex}
            />
          );
        }
        if (event.type === "error") {
          return <div key={i} className="text-destructive">{event.content}</div>;
        }
        if (event.type === "stopped") {
          return (
            <div key={i} className="flex items-center gap-1 text-xs text-muted-foreground">
              <CircleStop className="size-3" />
              <span>已手动停止</span>
            </div>
          );
        }
        return null;
      })}
      {finalAnswer?.content && <MarkdownRenderer content={finalAnswer.content} />}
    </div>
  );
}
