import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import type { DateRange } from "react-day-picker";
import { endOfDay, format, startOfDay } from "date-fns";
import { toast } from "sonner";
import { listAgents } from "@/services/agent";
import { listHistorySessions, batchDeleteConversations, deleteAllConversations, type HistorySessionsQuery } from "@/services/conversation";
import type { AgentDTO, ConversationHistoryDTO, PageResult } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious,
} from "@/components/ui/pagination";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { CalendarIcon, ExternalLink, History, RotateCcw, Search, Trash2 } from "lucide-react";

const DEFAULT_SIZE = 20;
const ALL_VALUE = "ALL";

const TYPE_OPTIONS = [
  { value: ALL_VALUE, label: "全部" },
  { value: "CHAT", label: "对话" },
  { value: "SCHEDULED", label: "定时任务" },
];

interface Filters {
  type: string;
  agentId: string;
  keyword: string;
  range?: DateRange;
}

const defaultFilters: Filters = {
  type: ALL_VALUE,
  agentId: ALL_VALUE,
  keyword: "",
  range: undefined,
};

function formatTimestamp(ts: number): string {
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatRange(range?: DateRange): string {
  if (!range?.from) return "选择时间范围";
  const from = format(range.from, "yyyy-MM-dd");
  if (!range.to) return from;
  return `${from} ~ ${format(range.to, "yyyy-MM-dd")}`;
}

export function HistorySessionsPage() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState<Filters>(defaultFilters);
  const [data, setData] = useState<PageResult<ConversationHistoryDTO>>({ list: [], total: 0, current: 1, pageSize: DEFAULT_SIZE });
  const [loading, setLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [clearAllOpen, setClearAllOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    listAgents().then(setAgents).catch(() => {});
  }, []);

  const load = useCallback(async (page: number, currentFilters: Filters) => {
    setLoading(true);
    try {
      const query: HistorySessionsQuery = { page, size: DEFAULT_SIZE };
      if (currentFilters.type !== ALL_VALUE) query.type = currentFilters.type;
      if (currentFilters.agentId !== ALL_VALUE) query.agentId = Number(currentFilters.agentId);
      if (currentFilters.keyword.trim()) query.keyword = currentFilters.keyword.trim();
      if (currentFilters.range?.from) query.startTime = startOfDay(currentFilters.range.from).getTime();
      if (currentFilters.range?.to) query.endTime = endOfDay(currentFilters.range.to).getTime();
      const result = await listHistorySessions(query);
      setData(result);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(1, appliedFilters);
  }, [appliedFilters, load]);

  const handleQuery = () => {
    setAppliedFilters(filters);
    setSelectedIds(new Set());
  };

  const handleReset = () => {
    setFilters(defaultFilters);
    setAppliedFilters(defaultFilters);
    setSelectedIds(new Set());
  };

  const handlePageChange = (page: number) => {
    load(page, appliedFilters);
  };

  const handleToggleSelect = (id: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleTogglePageSelect = () => {
    const pageIds = data.list.map((item) => item.id);
    const allSelected = pageIds.every((id) => selectedIds.has(id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allSelected) {
        pageIds.forEach((id) => next.delete(id));
      } else {
        pageIds.forEach((id) => next.add(id));
      }
      return next;
    });
  };

  const handleDelete = async (id: number) => {
    try {
      await batchDeleteConversations([id]);
      toast.success("已删除");
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
      load(data.current, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const handleBatchDelete = async () => {
    if (selectedIds.size === 0) return;
    setDeleting(true);
    try {
      await batchDeleteConversations(Array.from(selectedIds));
      toast.success(`已删除 ${selectedIds.size} 条会话`);
      setSelectedIds(new Set());
      setBatchDeleteOpen(false);
      load(data.current, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const handleClearAll = async () => {
    setDeleting(true);
    try {
      await deleteAllConversations();
      toast.success("已清空所有会话");
      setSelectedIds(new Set());
      setClearAllOpen(false);
      load(1, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "清空失败");
    } finally {
      setDeleting(false);
    }
  };

  const handleJump = (item: ConversationHistoryDTO) => {
    if (item.type === "CHAT") {
      navigate(`/?agentId=${item.agentId}&conversationId=${item.id}`);
    }
  };

  const typeLabel = (type: string | null) => {
    if (type === "CHAT") return "对话";
    if (type === "SCHEDULED") return "定时任务";
    return type ?? "-";
  };

  const pageIds = useMemo(() => data.list.map((item) => item.id), [data.list]);
  const pageSelectedAll = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));
  const pageSelectedPartial = pageIds.some((id) => selectedIds.has(id)) && !pageSelectedAll;

  const totalPages = Math.max(1, Math.ceil(data.total / data.pageSize));
  const pageNumbers = useMemo(() => {
    const pages: (number | "ellipsis")[] = [];
    const maxVisible = 7;
    if (totalPages <= maxVisible) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
      return pages;
    }
    pages.push(1);
    const start = Math.max(2, data.current - 2);
    const end = Math.min(totalPages - 1, data.current + 2);
    if (start > 2) pages.push("ellipsis");
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 1) pages.push("ellipsis");
    pages.push(totalPages);
    return pages;
  }, [data.current, totalPages]);

  return (
    <div className="flex flex-col gap-4 p-4">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold flex items-center gap-2">
          <History className="size-5" />
          历史会话
        </h1>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4">
        <div className="flex flex-col gap-1.5 min-w-[140px]">
          <Label className="text-xs text-muted-foreground">类型</Label>
          <Select value={filters.type} onValueChange={(value) => setFilters((prev) => ({ ...prev, type: value }))}>
            <SelectTrigger className="w-[140px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TYPE_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1.5 min-w-[160px]">
          <Label className="text-xs text-muted-foreground">智能体</Label>
          <Select value={filters.agentId} onValueChange={(value) => setFilters((prev) => ({ ...prev, agentId: value }))}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="全部" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_VALUE}>全部</SelectItem>
              {agents.map((agent) => (
                <SelectItem key={agent.id} value={String(agent.id)}>{agent.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex flex-col gap-1.5 min-w-[240px]">
          <Label className="text-xs text-muted-foreground">时间范围</Label>
          <Popover>
            <PopoverTrigger asChild>
              <Button variant="outline" className="w-[240px] justify-start text-left font-normal">
                <CalendarIcon className="mr-2 size-4" />
                {formatRange(filters.range)}
              </Button>
            </PopoverTrigger>
            <PopoverContent className="w-auto p-0" align="start">
              <Calendar
                mode="range"
                selected={filters.range}
                onSelect={(range) => setFilters((prev) => ({ ...prev, range }))}
                numberOfMonths={2}
                disabled={{ after: new Date() }}
              />
            </PopoverContent>
          </Popover>
        </div>

        <div className="flex flex-col gap-1.5 min-w-[200px] flex-1">
          <Label className="text-xs text-muted-foreground">关键字</Label>
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
            <Input
              placeholder="搜索会话标题"
              className="pl-9"
              value={filters.keyword}
              onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
              onKeyDown={(e) => e.key === "Enter" && handleQuery()}
            />
          </div>
        </div>

        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={handleReset}>
            <RotateCcw className="mr-1 size-4" />
            重置
          </Button>
          <Button onClick={handleQuery}>
            <Search className="mr-1 size-4" />
            查询
          </Button>
        </div>
      </div>

      <div className="flex items-center justify-between">
        <div className="text-sm text-muted-foreground">
          共 {data.total} 条
          {selectedIds.size > 0 && <span className="ml-2">已选 {selectedIds.size} 条</span>}
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="destructive"
            disabled={selectedIds.size === 0}
            onClick={() => setBatchDeleteOpen(true)}
          >
            <Trash2 className="mr-1 size-4" />
            批量删除
          </Button>
          <Button variant="outline" onClick={() => setClearAllOpen(true)}>
            清空
          </Button>
        </div>
      </div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-12">
                <Checkbox
                  checked={pageSelectedPartial ? "indeterminate" : pageSelectedAll}
                  onCheckedChange={handleTogglePageSelect}
                />
              </TableHead>
              <TableHead>标题</TableHead>
              <TableHead className="w-28">类型</TableHead>
              <TableHead className="w-40">智能体</TableHead>
              <TableHead className="w-44">最后更新时间</TableHead>
              <TableHead className="w-24 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-32 text-center text-muted-foreground">
                  加载中...
                </TableCell>
              </TableRow>
            ) : data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="h-32 text-center text-muted-foreground">
                  暂无会话
                </TableCell>
              </TableRow>
            ) : (
              data.list.map((item) => (
                <TableRow key={item.id}>
                  <TableCell>
                    <Checkbox
                      checked={selectedIds.has(item.id)}
                      onCheckedChange={() => handleToggleSelect(item.id)}
                    />
                  </TableCell>
                  <TableCell className="font-medium max-w-xs truncate" title={item.title ?? undefined}>
                    {item.title || "未命名会话"}
                  </TableCell>
                  <TableCell>
                    <Badge variant={item.type === "CHAT" ? "default" : "secondary"}>
                      {typeLabel(item.type)}
                    </Badge>
                  </TableCell>
                  <TableCell className="max-w-40 truncate" title={item.agentName}>
                    {item.agentName}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatTimestamp(item.updatedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        disabled={item.type !== "CHAT"}
                        onClick={() => handleJump(item)}
                        title="跳转到对话"
                      >
                        <ExternalLink className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => handleDelete(item.id)}
                        title="删除"
                      >
                        <Trash2 className="size-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <Pagination>
          <PaginationContent>
            <PaginationItem>
              <PaginationPrevious
                onClick={() => data.current > 1 && handlePageChange(data.current - 1)}
                className={data.current <= 1 ? "pointer-events-none opacity-50" : ""}
                text="上一页"
              />
            </PaginationItem>
            {pageNumbers.map((page, idx) => (
              <PaginationItem key={`${page}-${idx}`}>
                {page === "ellipsis" ? (
                  <PaginationEllipsis />
                ) : (
                  <PaginationLink
                    isActive={page === data.current}
                    onClick={() => handlePageChange(page)}
                  >
                    {page}
                  </PaginationLink>
                )}
              </PaginationItem>
            ))}
            <PaginationItem>
              <PaginationNext
                onClick={() => data.current < totalPages && handlePageChange(data.current + 1)}
                className={data.current >= totalPages ? "pointer-events-none opacity-50" : ""}
                text="下一页"
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}

      <AlertDialog open={batchDeleteOpen} onOpenChange={setBatchDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认批量删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除选中的 {selectedIds.size} 条会话吗？相关消息和事件将被删除，tokens 统计不受影响。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleBatchDelete} disabled={deleting}>
              {deleting ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={clearAllOpen} onOpenChange={setClearAllOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认清空所有会话</AlertDialogTitle>
            <AlertDialogDescription>
              确定要清空系统中的所有会话吗？相关消息和事件将被删除，tokens 统计不受影响。此操作不可恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleClearAll} disabled={deleting}>
              {deleting ? "清空中..." : "确认清空"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
