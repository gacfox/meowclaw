import React, { useState, useRef, useEffect, useCallback } from "react";
import { Search, Loader2 } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  conversationService,
  type ConversationDto,
} from "@/services/conversation";

interface SearchConversationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelectConversation: (
    conversation: ConversationDto & { id: number },
  ) => void;
}

export const SearchConversationDialog: React.FC<
  SearchConversationDialogProps
> = ({ open, onOpenChange, onSelectConversation }) => {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<(ConversationDto & { id: number })[]>(
    [],
  );
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(false);
  const [searched, setSearched] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const pageSize = 20;

  useEffect(() => {
    if (open) {
      setKeyword("");
      setResults([]);
      setSearched(false);
      setPage(1);
      setHasMore(false);
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }, [open]);

  const doSearch = useCallback(
    async (searchKeyword: string, pageNum: number, append: boolean) => {
      if (!searchKeyword.trim()) return;

      if (pageNum === 1) {
        setIsLoading(true);
      } else {
        setIsLoadingMore(true);
      }

      try {
        const response = await conversationService.list({
          keyword: searchKeyword.trim(),
          page: pageNum,
          pageSize,
        });

        if (response.code === 200 && response.data) {
          const items = response.data.items.filter(
            (item): item is ConversationDto & { id: number } =>
              typeof item.id === "number",
          );
          if (append) {
            setResults((prev) => [...prev, ...items]);
          } else {
            setResults(items);
          }
          setHasMore(pageNum < response.data.totalPages);
        }
      } catch (error) {
        console.error("搜索失败", error);
      } finally {
        setIsLoading(false);
        setIsLoadingMore(false);
        setSearched(true);
      }
    },
    [],
  );

  const handleSearch = () => {
    if (!keyword.trim()) return;
    setPage(1);
    setResults([]);
    doSearch(keyword, 1, false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSearch();
    }
  };

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.target as HTMLDivElement;
    const { scrollTop, scrollHeight, clientHeight } = target;
    if (
      scrollHeight - scrollTop - clientHeight < 100 &&
      hasMore &&
      !isLoadingMore &&
      !isLoading
    ) {
      const nextPage = page + 1;
      setPage(nextPage);
      doSearch(keyword, nextPage, true);
    }
  };

  const handleSelect = (conversation: ConversationDto & { id: number }) => {
    onSelectConversation(conversation);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>搜索对话</DialogTitle>
        </DialogHeader>
        <div className="flex gap-2">
          <Input
            ref={inputRef}
            placeholder="输入关键字搜索对话内容..."
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={handleKeyDown}
          />
          <Button
            onClick={handleSearch}
            disabled={isLoading || !keyword.trim()}
          >
            {isLoading ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Search className="h-4 w-4" />
            )}
          </Button>
        </div>
        <ScrollArea className="h-80" ref={scrollRef} onScroll={handleScroll}>
          <div className="space-y-1 pr-4">
            {isLoading && results.length === 0 ? (
              <>
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="p-3 rounded-lg border">
                    <Skeleton className="h-4 w-3/4 mb-2" />
                    <Skeleton className="h-3 w-1/2" />
                  </div>
                ))}
              </>
            ) : results.length > 0 ? (
              <>
                {results.map((conv) => (
                  <Button
                    key={conv.id}
                    variant="ghost"
                    className="w-full justify-start text-left h-auto py-3 px-3"
                    onClick={() => handleSelect(conv)}
                  >
                    <div className="flex flex-col items-start gap-1 min-w-0">
                      <span className="font-medium truncate">{conv.title}</span>
                      {conv.agentName && (
                        <span className="text-xs text-muted-foreground">
                          {conv.agentName}
                        </span>
                      )}
                    </div>
                  </Button>
                ))}
                {isLoadingMore && (
                  <>
                    {Array.from({ length: 3 }).map((_, i) => (
                      <div
                        key={`loading-${i}`}
                        className="p-3 rounded-lg border"
                      >
                        <Skeleton className="h-4 w-3/4 mb-2" />
                        <Skeleton className="h-3 w-1/2" />
                      </div>
                    ))}
                  </>
                )}
              </>
            ) : searched ? (
              <div className="text-center text-muted-foreground py-8">
                未找到相关对话
              </div>
            ) : (
              <div className="text-center text-muted-foreground py-8">
                输入关键字搜索对话内容
              </div>
            )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  );
};
