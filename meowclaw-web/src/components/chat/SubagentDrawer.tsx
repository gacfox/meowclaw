import { useEffect, useState } from "react";
import type { ChatEventBatchDTO } from "@/types";
import { listBatches } from "@/services/conversation";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { BatchBubble } from "@/components/chat/ChatEventBubble";
import { Bot, Loader2 } from "lucide-react";

/**
 * 子智能体运行流程查看抽屉：展示子会话批次，运行中每2秒轮询刷新
 */
export function SubagentDrawer({
  childConversationId,
  description,
  open,
  onClose,
}: {
  childConversationId: number;
  description?: string;
  open: boolean;
  onClose: () => void;
}) {
  const [batches, setBatches] = useState<ChatEventBatchDTO[]>([]);
  const [running, setRunning] = useState(false);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const load = async () => {
      try {
        const list = await listBatches(childConversationId);
        if (cancelled) return;
        setBatches(list);
        const anyRunning = list.some((b) => b.status === "RUNNING");
        setRunning(anyRunning);
        if (anyRunning) {
          timer = setTimeout(load, 2000);
        }
      } catch { /* 忽略轮询失败 */ }
    };
    load();
    return () => {
      cancelled = true;
      if (timer) clearTimeout(timer);
    };
  }, [open, childConversationId]);

  return (
    <Sheet open={open} onOpenChange={(o) => { if (!o) onClose(); }}>
      <SheetContent side="right" className="w-[520px] sm:max-w-[520px] overflow-y-auto">
        <SheetHeader>
          <SheetTitle className="flex items-center gap-2 text-sm">
            <Bot className="size-4" />
            子智能体：{description ?? "子任务"}
            {running && <Loader2 className="size-3.5 animate-spin text-muted-foreground" />}
          </SheetTitle>
        </SheetHeader>
        <div className="space-y-4 p-4">
          {batches.length === 0 && (
            <div className="py-8 text-center text-sm text-muted-foreground">加载中...</div>
          )}
          {batches.map((batch) => (
            <div key={batch.id} className="space-y-2">
              <div className="max-w-[90%] rounded-lg bg-primary px-3 py-2 text-sm text-primary-foreground">
                <div className="whitespace-pre-wrap">{batch.userContent}</div>
              </div>
              <BatchBubble events={batch.events ?? []} />
              {batch.status === "ERROR" && batch.errorMessage && (
                <div className="text-sm text-destructive">{batch.errorMessage}</div>
              )}
            </div>
          ))}
        </div>
      </SheetContent>
    </Sheet>
  );
}
