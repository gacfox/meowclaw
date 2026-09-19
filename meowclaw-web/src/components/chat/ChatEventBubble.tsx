import type { ChatEventDTO } from "@/types";
import { Table, TableBody, TableCell, TableRow } from "@/components/ui/table";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { Check, ChevronRight, Wrench } from "lucide-react";

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
}: {
  name: string;
  args?: string;
  result?: string;
}) {
  const parsed = parseToolArgs(args);
  return (
    <details className="group text-xs">
      <summary className="flex cursor-pointer items-center gap-1 text-muted-foreground list-none [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
        <Wrench className="size-3" />
        {name}
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
          <pre className="whitespace-pre-wrap rounded bg-muted p-2 text-xs">{result ?? "(无结果)"}</pre>
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

export function BatchBubble({ events }: { events: ChatEventDTO[] }) {
  const toolResults = new Map<string, string>();
  for (const e of events) {
    if (e.type === "tool_result" && e.toolCallId) {
      toolResults.set(e.toolCallId, e.content ?? "");
    }
  }
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
          const result = event.toolCallId ? toolResults.get(event.toolCallId) : undefined;
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
        if (event.type === "error") {
          return <div key={i} className="text-destructive">{event.content}</div>;
        }
        return null;
      })}
      {finalAnswer?.content && <MarkdownRenderer content={finalAnswer.content} />}
    </div>
  );
}
