import type { TraceDetail } from "@/types";
import { BatchBubble } from "@/components/chat/ChatEventBubble";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { ArrowDown, ArrowUp, Database } from "lucide-react";
import { LlmCallCard } from "./LlmCallCard";
import { formatTraceDuration, formatTraceTimestamp, traceStatusBadge } from "./traceUtils";

interface TraceDetailSheetProps {
  detail: TraceDetail | null;
  loading: boolean;
  onClose: () => void;
  onOpenConversation: () => void;
}

export function TraceDetailSheet({ detail, loading, onClose, onOpenConversation }: TraceDetailSheetProps) {
  return (
    <Sheet open={detail !== null || loading} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent className="overflow-y-auto px-4 pb-4 data-[side=right]:sm:max-w-5xl">
        {loading && !detail ? (
          <div className="py-16 text-center text-muted-foreground">加载中...</div>
        ) : detail && (
          <>
            <SheetHeader>
              <SheetTitle className="flex items-center gap-2">
                批次详情
                {traceStatusBadge(detail.status)}
              </SheetTitle>
            </SheetHeader>
            <div className="flex flex-col gap-5">
              <div className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-3">
                <div>
                  <div className="text-xs text-muted-foreground">智能体</div>
                  {detail.agentName ?? "-"}
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">会话</div>
                  <button className="text-primary hover:underline" onClick={onOpenConversation}>
                    {detail.conversationTitle ?? `会话#${detail.conversationId}`}
                  </button>
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">耗时</div>
                  {formatTraceDuration(detail)}
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">tokens</div>
                  <span className="mr-1 inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{detail.inputTokens ?? "-"}</span>
                  <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{detail.outputTokens ?? "-"}</span>
                  {detail.cachedTokens != null && detail.cachedTokens > 0 && (
                    <span title="缓存输入 tokens" className="ml-1 inline-flex items-center gap-0.5">
                      <Database className="size-3" />
                      {detail.cachedTokens}
                    </span>
                  )}
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">开始时间</div>
                  {formatTraceTimestamp(detail.createdAt)}
                </div>
                <div>
                  <div className="text-xs text-muted-foreground">完成时间</div>
                  {formatTraceTimestamp(detail.completedAt)}
                </div>
              </div>
              {detail.errorMessage && (
                <div className="rounded bg-destructive/10 p-3 text-sm text-destructive">{detail.errorMessage}</div>
              )}
              <div>
                <div className="mb-1 text-xs text-muted-foreground">用户输入</div>
                <div className="rounded-md bg-muted p-3 text-sm">
                  {detail.attachments && detail.attachments.length > 0 && (
                    <div className="mb-2 flex flex-wrap gap-2">
                      {detail.attachments.map((att) => (
                        <a key={att.name} href={att.url} target="_blank" rel="noreferrer">
                          <img src={att.url} alt={att.name} className="max-h-32 rounded" />
                        </a>
                      ))}
                    </div>
                  )}
                  <p className="whitespace-pre-wrap">{detail.userContent}</p>
                </div>
              </div>
              <div>
                <div className="mb-1 text-xs text-muted-foreground">事件时间线（{detail.events.length}）</div>
                <BatchBubble events={detail.events} />
              </div>
              <div>
                <div className="mb-1 text-xs text-muted-foreground">LLM 调用（{detail.llmCalls.length}）</div>
                <div className="flex flex-col gap-2">
                  {detail.llmCalls.map((call) => (
                    <LlmCallCard key={call.id} call={call} />
                  ))}
                  {detail.llmCalls.length === 0 && (
                    <div className="py-4 text-center text-xs text-muted-foreground">无调用记录</div>
                  )}
                </div>
              </div>
            </div>
          </>
        )}
      </SheetContent>
    </Sheet>
  );
}
