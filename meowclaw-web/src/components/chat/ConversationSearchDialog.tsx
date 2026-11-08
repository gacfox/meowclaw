import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, Search } from "lucide-react";
import { toast } from "sonner";
import { listHistorySessions } from "@/services/conversation";
import type { ConversationHistoryDTO, PageResult } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";

const PAGE_SIZE = 20;

interface ConversationSearchDialogProps {
  agentId: number | null;
  onSelect: (conversationId: number) => void;
}

function formatUpdatedAt(timestamp: number): string {
  return new Date(timestamp).toLocaleDateString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

export function ConversationSearchDialog({ agentId, onSelect }: ConversationSearchDialogProps) {
  const [open, setOpen] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [items, setItems] = useState<ConversationHistoryDTO[]>([]);
  const [result, setResult] = useState<PageResult<ConversationHistoryDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const requestIdRef = useRef(0);
  const scrollRef = useRef<HTMLDivElement>(null);

  const load = useCallback(async (query: string, page: number, append: boolean) => {
    if (agentId == null || !query) return;
    const requestId = ++requestIdRef.current;
    if (append) setLoadingMore(true);
    else setLoading(true);
    try {
      const response = await listHistorySessions({
        agentId,
        type: "CHAT",
        keyword: query,
        page,
        size: PAGE_SIZE,
      });
      if (requestId !== requestIdRef.current) return;
      setResult(response);
      setItems((previous) => {
        if (!append) return response.list;
        const existingIds = new Set(previous.map((item) => item.id));
        return [...previous, ...response.list.filter((item) => !existingIds.has(item.id))];
      });
    } catch (error) {
      if (requestId === requestIdRef.current) {
        toast.error(error instanceof Error ? error.message : "搜索会话失败");
      }
    } finally {
      if (requestId === requestIdRef.current) {
        if (append) setLoadingMore(false);
        else setLoading(false);
      }
    }
  }, [agentId]);

  useEffect(() => {
    if (!open) return;
    const timer = setTimeout(() => {
      const query = keyword.trim();
      if (!query) {
        requestIdRef.current += 1;
        setItems([]);
        setResult(null);
        setLoading(false);
        setLoadingMore(false);
        return;
      }
      if (scrollRef.current) scrollRef.current.scrollTop = 0;
      void load(query, 1, false);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword, open, load]);

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen);
    requestIdRef.current += 1;
    setKeyword("");
    setItems([]);
    setResult(null);
    setLoading(false);
    setLoadingMore(false);
  };

  const handleScroll = () => {
    const element = scrollRef.current;
    const query = keyword.trim();
    if (!element || !query || !result || loading || loadingMore) return;
    if (items.length >= result.total) return;
    if (element.scrollTop + element.clientHeight < element.scrollHeight - 24) return;
    void load(query, result.current + 1, true);
  };

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          size="icon"
          title="搜索历史会话"
          aria-label="搜索历史会话"
          disabled={agentId == null}
        >
          <Search className="size-4" />
        </Button>
      </DialogTrigger>
      <DialogContent className="flex h-[min(560px,calc(100dvh-4rem))] flex-col gap-4 p-5 sm:max-w-2xl">
        <DialogHeader className="pr-8">
          <DialogTitle>搜索历史会话</DialogTitle>
        </DialogHeader>
        <div className="relative shrink-0">
          <Search className="absolute left-2.5 top-3.5 size-4 text-muted-foreground" />
          <Input
            autoFocus
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索当前智能体的历史会话"
            className="h-11 pl-9 text-base"
          />
        </div>

        <div ref={scrollRef} onScroll={handleScroll} className="min-h-0 flex-1 overflow-y-auto">
          {!keyword.trim() ? (
            <div className="flex h-full items-center justify-center px-2 text-sm text-muted-foreground">
              输入关键字搜索历史会话
            </div>
          ) : loading ? (
            <div className="flex h-full items-center justify-center gap-2 px-2 text-sm text-muted-foreground">
              <Loader2 className="size-4 animate-spin" />
              搜索中...
            </div>
          ) : items.length === 0 ? (
            <div className="flex h-full items-center justify-center px-2 text-sm text-muted-foreground">
              未找到相关会话
            </div>
          ) : (
            <div className="flex flex-col gap-0.5">
              {items.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  className="flex w-full flex-col gap-1 rounded-md px-3 py-2.5 text-left hover:bg-accent"
                  onClick={() => {
                    onSelect(item.id);
                    handleOpenChange(false);
                  }}
                >
                  <span className="w-full truncate text-sm font-medium">{item.title || "未命名会话"}</span>
                  <span className="text-xs text-muted-foreground">更新于 {formatUpdatedAt(item.updatedAt)}</span>
                </button>
              ))}
              {loadingMore && (
                <div className="flex items-center justify-center gap-2 py-2 text-xs text-muted-foreground">
                  <Loader2 className="size-3.5 animate-spin" />
                  加载更多...
                </div>
              )}
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}
