import { memo, useEffect, useRef, useState } from "react";
import type { ReactNode } from "react";
import type { ChatEventDTO } from "@/types";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { parseApprovalContent } from "@/lib/approval";
import type { ApprovalContent } from "@/lib/approval";
import { parseSubagentContent } from "@/lib/subagent";
import type { SubagentContent } from "@/lib/subagent";
import { cn } from "@/lib/utils";
import { Bot, Brain, Check, ChevronDown, ChevronRight, CircleStop, Image as ImageIcon, Loader2, ShieldCheck, ShieldX, Wrench, X } from "lucide-react";
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

function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)}MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${bytes}B`;
}

function parseImageMarker(result: string | undefined): { mimeType: string; bytes: number } | null {
  if (!result || !result.startsWith("<<<image>>>")) return null;
  try {
    const firstLine = result.slice("<<<image>>>".length).split("\n", 1)[0];
    const obj = JSON.parse(firstLine);
    if (obj && typeof obj.mimeType === "string") return { mimeType: obj.mimeType, bytes: Number(obj.bytes) || 0 };
    return null;
  } catch {
    return null;
  }
}

function ToolCallContent({
  parsedArgs,
  result,
  pending,
  className,
}: {
  parsedArgs: Record<string, unknown> | null;
  result?: string;
  pending?: boolean;
  className?: string;
}) {
  const imageMarker = parseImageMarker(result);
  return (
    <div className={cn("max-w-full space-y-2 overflow-x-auto", className)}>
      {parsedArgs && Object.keys(parsedArgs).length > 0 && (
        <Table>
          <TableBody>
            {Object.entries(parsedArgs).map(([key, value]) => {
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
        {imageMarker ? (
          <div className="flex items-center gap-1.5 rounded bg-muted p-2 text-xs">
            <ImageIcon className="size-3.5 shrink-0 text-muted-foreground" />
            图片已读取并发送给模型 · {imageMarker.mimeType} · {formatBytes(imageMarker.bytes)}
          </div>
        ) : (
          <pre className="whitespace-pre-wrap rounded bg-muted p-2 text-xs">{result ?? (pending ? "执行中..." : "(无结果)")}</pre>
        )}
      </div>
    </div>
  );
}

function toolArgsPreview(args: Record<string, unknown> | null): string | null {
  if (!args) return null;
  const entries = Object.entries(args);
  if (entries.length === 0) return null;
  const [key, value] = entries[0];
  const preview = truncateSingleLine(`${key}: ${formatArgValue(value)}`, 80).text;
  return entries.length > 1 ? `${preview} · +${entries.length - 1}` : preview;
}

export function ConversationToolCall({
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
  const parsedArgs = parseToolArgs(args);
  const preview = toolArgsPreview(parsedArgs);
  return (
    <details className="group overflow-hidden rounded-lg border bg-card text-xs text-card-foreground">
      <summary className="flex min-w-0 cursor-pointer items-center gap-2 px-3 py-2.5 list-none transition-colors hover:bg-muted/50 [&::-webkit-details-marker]:hidden">
        <Wrench className="size-3.5 shrink-0 text-muted-foreground" />
        <span className="max-w-[45%] shrink-0 truncate font-medium" title={name}>{name}</span>
        {preview && <span className="min-w-0 flex-1 truncate text-muted-foreground">{preview}</span>}
        {pending ? (
          <span className="ml-auto flex shrink-0 items-center gap-1 text-muted-foreground">
            <Loader2 className="size-3 animate-spin" />
            执行中
          </span>
        ) : result !== undefined ? (
          <span className="ml-auto flex shrink-0 items-center gap-1 text-muted-foreground">
            <Check className="size-3 text-green-500" />
            完成
          </span>
        ) : !preview ? <span className="flex-1" /> : null}
        <ChevronRight className="size-3.5 shrink-0 text-muted-foreground transition-transform group-open:rotate-90" />
      </summary>
      <div className="border-t px-3 py-3">
        <ToolCallContent parsedArgs={parsedArgs} result={result} pending={pending} />
      </div>
    </details>
  );
}

export function ThinkingPreview({ content, streaming = false }: { content?: string | null; streaming?: boolean }) {
  const [expanded, setExpanded] = useState(false);
  const previewRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const preview = previewRef.current;
    if (!preview || expanded) return;
    preview.scrollTop = streaming ? preview.scrollHeight : 0;
  }, [content, streaming, expanded]);

  return (
    <section className="text-xs">
      <div className="flex items-center gap-1.5 text-muted-foreground">
        <Brain className="size-3.5 shrink-0" />
        <span className="font-medium text-foreground">思考</span>
      </div>
      <div className="ml-[7px] mt-1 border-l pl-2 sm:pl-3">
        <div
          ref={previewRef}
          className={cn(
            "whitespace-pre-wrap break-words leading-6 text-muted-foreground",
            expanded ? "max-h-none overflow-visible" : "max-h-[4.5rem] overflow-y-hidden",
          )}
        >
          {content}
        </div>
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
          className="mt-1 inline-flex items-center gap-0.5 text-muted-foreground transition-colors hover:text-foreground"
        >
          {expanded ? "收起思考" : "展开完整思考"}
          <ChevronDown className={cn("size-3 transition-transform", expanded && "rotate-180")} />
        </button>
      </div>
    </section>
  );
}

export function ExecutionProcess({
  thinkingCount,
  toolCount,
  running,
  defaultOpen = true,
  children,
}: {
  thinkingCount: number;
  toolCount: number;
  running?: boolean;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  const summary = [
    thinkingCount > 0 ? `已思考 ${thinkingCount} 次` : null,
    toolCount > 0 ? `${toolCount} 个工具` : null,
  ].filter(Boolean).join(" · ") || "查看执行详情";
  return (
    <section className="overflow-hidden rounded-lg border bg-card text-card-foreground">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="flex w-full min-w-0 items-center gap-2 px-3 py-2 text-left text-xs transition-colors hover:bg-muted/50"
      >
        <Brain className="size-3.5 shrink-0 text-muted-foreground" />
        <span className="shrink-0 font-medium">执行过程</span>
        <span className="min-w-0 truncate text-muted-foreground">{summary}</span>
        {running && <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />}
        <span className="ml-auto shrink-0 text-muted-foreground">{open ? "收起过程" : "展开过程"}</span>
        <ChevronDown className={cn("size-3.5 shrink-0 text-muted-foreground transition-transform", !open && "-rotate-90")} />
      </button>
      <div className={cn("border-t px-2 py-3 sm:px-3", !open && "hidden")}>
        <div className="space-y-3">{children}</div>
      </div>
    </section>
  );
}

interface ConversationTimelineEntry {
  id?: number;
  type: string;
  content?: string | null;
  toolCallId?: string | null;
  toolName?: string | null;
  toolArguments?: string | null;
  name?: string;
  args?: string;
  result?: string;
  approval?: ApprovalContent | null;
  subagent?: SubagentContent | null;
}

interface TimelineGroup {
  type: "content" | "process" | "terminal";
  entries: ConversationTimelineEntry[];
}

export function ConversationTimeline({
  entries,
  running = false,
  thinking = false,
  onOpenSubagent,
}: {
  entries: ConversationTimelineEntry[];
  running?: boolean;
  thinking?: boolean;
  onOpenSubagent?: (childConversationId: number, description?: string) => void;
}) {
  const groups: TimelineGroup[] = [];
  const toolResults = entries.filter((entry) => entry.type === "tool_result");
  let resultCursor = 0;
  for (const entry of entries) {
    if (entry.type === "tool_result" || entry.type === "subagent") continue;
    if (entry.type === "content") {
      groups.push({ type: "content", entries: [entry] });
      continue;
    }
    if (entry.type === "thinking" || entry.type === "tool_call" || entry.type === "approval") {
      let group = groups[groups.length - 1];
      if (group?.type !== "process") {
        group = { type: "process", entries: [] };
        groups.push(group);
      }
      if (entry.type === "tool_call") {
        const result = entry.result ?? toolResults[resultCursor++]?.content ?? undefined;
        group.entries.push({ ...entry, result });
      } else {
        group.entries.push(entry);
      }
      continue;
    }
    if (entry.type === "error" || entry.type === "stopped") {
      groups.push({ type: "terminal", entries: [entry] });
    }
  }

  return (
    <div className="w-full min-w-0 space-y-3 text-sm">
      {groups.map((group, groupIndex) => {
        if (group.type === "content") {
          return <div key={groupIndex} className="min-w-0"><MarkdownRenderer content={group.entries[0].content ?? ""} /></div>;
        }
        if (group.type === "terminal") {
          const event = group.entries[0];
          if (event.type === "error") {
            return <div key={groupIndex} className="text-destructive">{event.content}</div>;
          }
          return (
            <div key={groupIndex} className="flex items-center gap-1 text-xs text-muted-foreground">
              <CircleStop className="size-3" />
              <span>已手动停止</span>
            </div>
          );
        }
        return (
          <ExecutionProcess
            key={groupIndex}
            thinkingCount={group.entries.filter((entry) => entry.type === "thinking").length}
            toolCount={group.entries.filter((entry) => entry.type === "tool_call").length}
            defaultOpen={running}
            running={running && groupIndex === groups.length - 1}
          >
            {group.entries.map((entry, entryIndex) => {
              const key = entry.id ?? entryIndex;
              const name = entry.toolName ?? entry.name ?? "tool";
              const args = entry.toolArguments ?? entry.args;
              if (entry.type === "thinking") {
                return <ThinkingPreview key={key} content={entry.content} streaming={running} />;
              }
              if (entry.type === "approval") {
                const info = entry.approval ?? parseApprovalContent(entry.content);
                return (
                  <ApprovalDetails
                    key={key}
                    name={name}
                    args={args}
                    decision={info?.decision ?? "pending"}
                    policyName={info?.policyName}
                    matchedRuleIndex={info?.matchedRuleIndex}
                  />
                );
              }
              if (name === "spawn_subagent") {
                const subEvent = entries.find((item) => item.type === "subagent" && item.toolCallId === entry.toolCallId);
                const info = entry.subagent ?? parseSubagentContent(subEvent?.content);
                return (
                  <SubagentDetails
                    key={key}
                    args={args}
                    description={info?.description}
                    status={info?.status ?? (running ? "running" : "completed")}
                    onOpen={info?.childConversationId != null && onOpenSubagent != null
                      ? () => onOpenSubagent(info.childConversationId, info.description)
                      : undefined}
                  />
                );
              }
              return (
                <ConversationToolCall
                  key={key}
                  name={name}
                  args={args}
                  result={entry.result}
                  pending={running && entry.result === undefined}
                />
              );
            })}
          </ExecutionProcess>
        );
      })}
      {thinking && (
        <div className="flex items-center gap-2 text-muted-foreground">
          <Loader2 className="size-3 animate-spin" />
          思考中...
        </div>
      )}
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
      <div className="mt-2 max-w-full space-y-2 overflow-x-auto rounded border bg-background/50 p-2">
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

const SUBAGENT_STATUS_LABELS: Record<string, string> = {
  running: "运行中",
  completed: "已完成",
  failed: "失败",
  stopped: "已停止",
};

export function SubagentDetails({
  description,
  args,
  status,
  onOpen,
}: {
  description?: string;
  args?: string;
  status?: string;
  onOpen?: () => void;
}) {
  const parsed = parseToolArgs(args);
  const desc = description ?? (typeof parsed?.description === "string" ? parsed.description : "子任务");
  const effectiveStatus = status ?? "running";
  return (
    <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <Bot className="size-3.5 shrink-0" />
      {onOpen ? (
        <button type="button" onClick={onOpen} className="truncate hover:text-foreground hover:underline" title="查看子任务运行流程">
          子智能体：{desc}
        </button>
      ) : (
        <span className="truncate">子智能体：{desc}</span>
      )}
      {effectiveStatus === "completed" ? (
        <Check className="size-3 shrink-0 text-green-500" />
      ) : effectiveStatus === "failed" ? (
        <X className="size-3 shrink-0 text-destructive" />
      ) : effectiveStatus === "stopped" ? (
        <CircleStop className="size-3 shrink-0" />
      ) : (
        <Loader2 className="size-3 shrink-0 animate-spin" />
      )}
      <span>{SUBAGENT_STATUS_LABELS[effectiveStatus] ?? effectiveStatus}</span>
    </div>
  );
}

export const BatchBubble = memo(function BatchBubble({
  events,
  onOpenSubagent,
}: {
  events: ChatEventDTO[];
  onOpenSubagent?: (childConversationId: number, description?: string) => void;
}) {
  return <ConversationTimeline entries={events} onOpenSubagent={onOpenSubagent} />;
});
