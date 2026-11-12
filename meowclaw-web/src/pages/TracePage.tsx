import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import type { DateRange } from "react-day-picker";
import { endOfDay, format, startOfDay } from "date-fns";
import { toast } from "sonner";
import { listAgents } from "@/services/agent";
import {
  batchDeleteTraces,
  deleteAllTraces,
  deleteTrace,
  getTraceDetail,
  getTraceRetentionPolicy,
  listTraces,
  updateTraceRetentionPolicy,
  type TraceQuery,
} from "@/services/trace";
import type { AgentDTO, LlmCallLogItem, PageResult, TraceDetail, TraceItem, TraceRetentionPolicy } from "@/types";
import { BatchBubble } from "@/components/chat/ChatEventBubble";
import { JsonViewer } from "@/components/json-viewer/JsonViewer";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Calendar } from "@/components/ui/calendar";
import { Checkbox } from "@/components/ui/checkbox";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuRadioGroup, DropdownMenuRadioItem, DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious,
} from "@/components/ui/pagination";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Badge } from "@/components/ui/badge";
import { ArrowDown, ArrowUp, CalendarIcon, ChevronDown, ChevronRight, RefreshCw, RotateCcw, Search, Trash2 } from "lucide-react";

const DEFAULT_SIZE = 20;
const ALL_VALUE = "ALL";

const STATUS_OPTIONS = [
  { value: ALL_VALUE, label: "全部" },
  { value: "COMPLETED", label: "已完成" },
  { value: "RUNNING", label: "运行中" },
  { value: "ERROR", label: "错误" },
];

const RETENTION_OPTIONS: { value: TraceRetentionPolicy; label: string }[] = [
  { value: "DAYS_3", label: "最近 3 天" },
  { value: "DAYS_7", label: "最近 7 天" },
  { value: "MONTH_1", label: "最近一个月" },
  { value: "YEAR_1", label: "最近一年" },
  { value: "ALL", label: "全部留存（默认）" },
];

const PURPOSE_LABELS: Record<string, string> = {
  agent: "主循环",
  title: "标题",
  recap: "摘要",
  memory: "记忆",
};

function statusBadge(status: string) {
  if (status === "RUNNING") return <Badge variant="secondary">运行中</Badge>;
  if (status === "ERROR") return <Badge variant="destructive">错误</Badge>;
  return <Badge variant="default">已完成</Badge>;
}

function formatTs(ts: number | null): string {
  if (ts == null) return "-";
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatDuration(batch: { createdAt: number; completedAt: number | null }): string {
  if (batch.completedAt == null) return "-";
  return `${((batch.completedAt - batch.createdAt) / 1000).toFixed(1)}s`;
}

function formatRange(range?: DateRange): string {
  if (!range?.from) return "选择时间范围";
  const from = format(range.from, "yyyy-MM-dd");
  if (!range.to) return from;
  return `${from} ~ ${format(range.to, "yyyy-MM-dd")}`;
}

function formatCacheHitRate(cachedTokens: number | null, inputTokens: number): string | null {
  if (cachedTokens == null || inputTokens <= 0) return null;
  return `${(cachedTokens / inputTokens * 100).toFixed(1)}%`;
}

function LlmCallCard({ call }: { call: LlmCallLogItem }) {
  const cacheHitRate = formatCacheHitRate(call.cachedTokens, call.inputTokens);
  return (
    <details className="group rounded-lg border text-sm">
      <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2 p-3 [&::-webkit-details-marker]:hidden">
        <ChevronRight className="size-3.5 shrink-0 transition-transform group-open:rotate-90" />
        <Badge variant="outline">{PURPOSE_LABELS[call.purpose ?? ""] ?? call.purpose ?? "未知"}</Badge>
        <span className="font-mono text-xs">{call.model}</span>
        {call.status === "ERROR"
          ? <Badge variant="destructive">失败</Badge>
          : <Badge variant="secondary">成功</Badge>}
        <span className="ml-auto flex items-center gap-2 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{call.inputTokens}</span>
          <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{call.outputTokens}</span>
          <span title="缓存输入 Tokens / 缓存命中率">
            缓存 {call.cachedTokens ?? "未上报"}{cacheHitRate != null && ` (${cacheHitRate})`}
          </span>
          {call.durationMs != null && <span>{(call.durationMs / 1000).toFixed(1)}s</span>}
          <span>{formatTs(call.createdAt)}</span>
        </span>
      </summary>
      <div className="flex flex-col gap-3 border-t p-3">
        {call.errorMessage && (
          <div className="rounded bg-destructive/10 p-2 text-xs text-destructive">{call.errorMessage}</div>
        )}
        {call.reasoningContent && (
          <details className="text-xs">
            <summary className="cursor-pointer text-muted-foreground">推理内容</summary>
            <pre className="mt-1 max-h-64 overflow-auto whitespace-pre-wrap rounded bg-muted p-2">{call.reasoningContent}</pre>
          </details>
        )}
        <div className="text-xs">
          <div className="mb-1 text-muted-foreground">请求消息</div>
          <JsonViewer raw={call.requestMessages} maxHeightClass="max-h-96" />
        </div>
        {call.responseContent != null && call.responseContent.trim() !== "" && (
          <div className="text-xs">
            <div className="mb-1 text-muted-foreground">响应内容</div>
            <pre className="max-h-64 overflow-auto whitespace-pre-wrap rounded bg-muted p-2">{call.responseContent}</pre>
          </div>
        )}
        {call.responseToolCalls && (
          <div className="text-xs">
            <div className="mb-1 text-muted-foreground">响应工具调用</div>
            <JsonViewer raw={call.responseToolCalls} maxHeightClass="max-h-64" />
          </div>
        )}
      </div>
    </details>
  );
}

interface Filters {
  agentId: string;
  status: string;
  keyword: string;
  range?: DateRange;
}

const defaultFilters: Filters = { agentId: ALL_VALUE, status: ALL_VALUE, keyword: "" };

export function TracePage() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState<Filters>(defaultFilters);
  const [data, setData] = useState<PageResult<TraceItem>>({ list: [], total: 0, current: 1, pageSize: DEFAULT_SIZE });
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteTarget, setDeleteTarget] = useState<TraceItem | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [clearAllOpen, setClearAllOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [retentionPolicy, setRetentionPolicy] = useState<TraceRetentionPolicy>("ALL");
  const [pendingRetentionPolicy, setPendingRetentionPolicy] = useState<TraceRetentionPolicy | null>(null);
  const [retentionSaving, setRetentionSaving] = useState(false);

  useEffect(() => {
    listAgents().then(setAgents).catch(() => {});
    getTraceRetentionPolicy()
      .then(setRetentionPolicy)
      .catch((err) => toast.error(err instanceof Error ? err.message : "加载数据留存策略失败"));
  }, []);

  const load = useCallback(async (page: number, currentFilters: Filters) => {
    setLoading(true);
    try {
      const query: TraceQuery = { page, size: DEFAULT_SIZE };
      if (currentFilters.agentId !== ALL_VALUE) query.agentId = Number(currentFilters.agentId);
      if (currentFilters.status !== ALL_VALUE) query.status = currentFilters.status;
      if (currentFilters.keyword.trim()) query.keyword = currentFilters.keyword.trim();
      if (currentFilters.range?.from) query.startTime = startOfDay(currentFilters.range.from).getTime();
      if (currentFilters.range?.to) query.endTime = endOfDay(currentFilters.range.to).getTime();
      setData(await listTraces(query));
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

  const handleToggleSelect = (batchId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(batchId)) next.delete(batchId);
      else next.add(batchId);
      return next;
    });
  };

  const handleTogglePageSelect = () => {
    const pageIds = data.list.filter((item) => item.status !== "RUNNING").map((item) => item.batchId);
    const allSelected = pageIds.length > 0 && pageIds.every((id) => selectedIds.has(id));
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (allSelected) pageIds.forEach((id) => next.delete(id));
      else pageIds.forEach((id) => next.add(id));
      return next;
    });
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleteTarget.status === "RUNNING") return;
    const batchId = deleteTarget.batchId;
    setDeleting(true);
    try {
      const deletedCount = await deleteTrace(batchId);
      toast.success(deletedCount > 0 ? "已删除追踪记录" : "追踪已不存在或正在运行");
      setSelectedIds((prev) => {
        const next = new Set(prev);
        next.delete(batchId);
        return next;
      });
      if (detail?.batchId === batchId) {
        setDetail(null);
        setDetailLoading(false);
      }
      setDeleteTarget(null);
      const remainingTotal = Math.max(0, data.total - deletedCount);
      const nextPage = Math.min(data.current, Math.max(1, Math.ceil(remainingTotal / data.pageSize)));
      await load(nextPage, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const handleBatchDelete = async () => {
    const batchIds = Array.from(selectedIds);
    if (batchIds.length === 0) return;
    setDeleting(true);
    try {
      const deletedCount = await batchDeleteTraces(batchIds);
      toast.success(`已删除 ${deletedCount} 条追踪记录`);
      setSelectedIds(new Set());
      if (detail && batchIds.includes(detail.batchId)) {
        setDetail(null);
        setDetailLoading(false);
      }
      setBatchDeleteOpen(false);
      const remainingTotal = Math.max(0, data.total - deletedCount);
      const nextPage = Math.min(data.current, Math.max(1, Math.ceil(remainingTotal / data.pageSize)));
      await load(nextPage, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const handleClearAll = async () => {
    setDeleting(true);
    try {
      await deleteAllTraces();
      toast.success("已清空追踪记录，运行中记录已保留");
      setSelectedIds(new Set());
      if (detail && detail.status !== "RUNNING") {
        setDetail(null);
        setDetailLoading(false);
      }
      setClearAllOpen(false);
      await load(1, appliedFilters);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "清空失败");
    } finally {
      setDeleting(false);
    }
  };

  const saveRetentionPolicy = async (policy: TraceRetentionPolicy) => {
    setRetentionSaving(true);
    try {
      await updateTraceRetentionPolicy(policy);
      setRetentionPolicy(policy);
      setPendingRetentionPolicy(null);
      toast.success("数据留存策略已保存");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存数据留存策略失败");
    } finally {
      setRetentionSaving(false);
    }
  };

  const handleRetentionPolicyChange = (policy: TraceRetentionPolicy) => {
    if (policy === retentionPolicy || retentionSaving) return;
    if (policy === "ALL") void saveRetentionPolicy(policy);
    else setPendingRetentionPolicy(policy);
  };

  const openDetail = async (batchId: number) => {
    setDetailLoading(true);
    try {
      setDetail(await getTraceDetail(batchId));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载失败");
    } finally {
      setDetailLoading(false);
    }
  };

  const selectablePageIds = useMemo(
    () => data.list.filter((item) => item.status !== "RUNNING").map((item) => item.batchId),
    [data.list],
  );
  const pageSelectedAll = selectablePageIds.length > 0 && selectablePageIds.every((id) => selectedIds.has(id));
  const pageSelectedPartial = selectablePageIds.some((id) => selectedIds.has(id)) && !pageSelectedAll;

  const totalPages = Math.max(1, Math.ceil(data.total / data.pageSize));
  const { current } = data;
  const pageNumbers = useMemo(() => {
    const pages: (number | "ellipsis")[] = [];
    const maxVisible = 7;
    if (totalPages <= maxVisible) {
      for (let i = 1; i <= totalPages; i++) pages.push(i);
      return pages;
    }
    pages.push(1);
    const start = Math.max(2, current - 2);
    const end = Math.min(totalPages - 1, current + 2);
    if (start > 2) pages.push("ellipsis");
    for (let i = start; i <= end; i++) pages.push(i);
    if (end < totalPages - 1) pages.push("ellipsis");
    pages.push(totalPages);
    return pages;
  }, [current, totalPages]);

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">追踪观测</h1>
        <div className="flex items-center gap-2">
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" disabled={retentionSaving}>
                数据留存策略
                <ChevronDown className="ml-1 size-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              <DropdownMenuRadioGroup
                value={retentionPolicy}
                onValueChange={(value) => handleRetentionPolicyChange(value as TraceRetentionPolicy)}
              >
                {RETENTION_OPTIONS.map((option) => (
                  <DropdownMenuRadioItem key={option.value} value={option.value} disabled={retentionSaving}>
                    {option.label}
                  </DropdownMenuRadioItem>
                ))}
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>
          <Button
            variant="outline"
            size="icon"
            onClick={() => load(data.current, appliedFilters)}
            title="刷新"
            aria-label="刷新"
            disabled={loading || deleting}
          >
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4">
        <div className="flex min-w-[160px] flex-col gap-1.5">
          <Label className="text-xs text-muted-foreground">智能体</Label>
          <Select value={filters.agentId} onValueChange={(v) => setFilters((prev) => ({ ...prev, agentId: v }))}>
            <SelectTrigger className="w-[160px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL_VALUE}>全部</SelectItem>
              {agents.map((agent) => (
                <SelectItem key={agent.id} value={String(agent.id)}>{agent.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex min-w-[120px] flex-col gap-1.5">
          <Label className="text-xs text-muted-foreground">状态</Label>
          <Select value={filters.status} onValueChange={(v) => setFilters((prev) => ({ ...prev, status: v }))}>
            <SelectTrigger className="w-[120px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="flex min-w-[200px] flex-col gap-1.5">
          <Label className="text-xs text-muted-foreground">关键字</Label>
          <div className="relative">
            <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
            <Input
              placeholder="搜索用户输入"
              className="pl-9"
              value={filters.keyword}
              onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
              onKeyDown={(e) => e.key === "Enter" && handleQuery()}
            />
          </div>
        </div>

        <div className="flex flex-col gap-1.5">
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
              />
            </PopoverContent>
          </Popover>
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
            disabled={selectedIds.size === 0 || deleting}
            onClick={() => setBatchDeleteOpen(true)}
          >
            <Trash2 className="mr-1 size-4" />
            批量删除
          </Button>
          <Button variant="destructive" disabled={deleting} onClick={() => setClearAllOpen(true)}>
            <Trash2 className="mr-1 size-4" />
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
                  aria-label="选择当前页"
                  checked={pageSelectedPartial ? "indeterminate" : pageSelectedAll}
                  disabled={selectablePageIds.length === 0 || deleting}
                  onCheckedChange={handleTogglePageSelect}
                />
              </TableHead>
              <TableHead className="w-44">时间</TableHead>
              <TableHead className="w-28">智能体</TableHead>
              <TableHead className="w-40">会话</TableHead>
              <TableHead>用户输入</TableHead>
              <TableHead className="w-20">状态</TableHead>
              <TableHead className="w-24">LLM调用</TableHead>
              <TableHead className="w-36">tokens</TableHead>
              <TableHead className="w-20">耗时</TableHead>
              <TableHead className="w-20 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} className="h-32 text-center text-muted-foreground">加载中...</TableCell>
              </TableRow>
            ) : data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={10} className="h-32 text-center text-muted-foreground">暂无数据</TableCell>
              </TableRow>
            ) : (
              data.list.map((item) => (
                <TableRow key={item.batchId} className="cursor-pointer" onClick={() => openDetail(item.batchId)}>
                  <TableCell onClick={(event) => event.stopPropagation()}>
                    <Checkbox
                      aria-label={`选择批次 ${item.batchId}`}
                      checked={selectedIds.has(item.batchId)}
                      disabled={item.status === "RUNNING" || deleting}
                      onCheckedChange={() => handleToggleSelect(item.batchId)}
                    />
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatTs(item.createdAt)}</TableCell>
                  <TableCell className="max-w-28 truncate">{item.agentName ?? "-"}</TableCell>
                  <TableCell className="max-w-40 truncate">
                    {item.conversationTitle ?? `会话#${item.conversationId}`}
                  </TableCell>
                  <TableCell className="max-w-md">
                    <span className="line-clamp-1" title={item.userContent}>{item.userContent}</span>
                  </TableCell>
                  <TableCell>{statusBadge(item.status)}</TableCell>
                  <TableCell>{item.llmCallCount}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{item.inputTokens ?? "-"}</span>
                    {" / "}
                    <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{item.outputTokens ?? "-"}</span>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatDuration(item)}</TableCell>
                  <TableCell className="text-right" onClick={(event) => event.stopPropagation()}>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      disabled={item.status === "RUNNING" || deleting}
                      onClick={(event) => {
                        event.stopPropagation();
                        setDeleteTarget(item);
                      }}
                      title={item.status === "RUNNING" ? "运行中的追踪不可删除" : "删除"}
                    >
                      <Trash2 className="size-4 text-destructive" />
                    </Button>
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
                onClick={() => data.current > 1 && load(data.current - 1, appliedFilters)}
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
                    onClick={() => load(page, appliedFilters)}
                  >
                    {page}
                  </PaginationLink>
                )}
              </PaginationItem>
            ))}
            <PaginationItem>
              <PaginationNext
                onClick={() => data.current < totalPages && load(data.current + 1, appliedFilters)}
                className={data.current >= totalPages ? "pointer-events-none opacity-50" : ""}
                text="下一页"
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open && !deleting) setDeleteTarget(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除追踪记录</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除批次 #{deleteTarget?.batchId} 的追踪记录吗？删除后不可恢复，不影响会话和 Tokens 统计。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleting}
              onClick={(event) => {
                event.preventDefault();
                void handleDelete();
              }}
            >
              {deleting ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={batchDeleteOpen} onOpenChange={(open) => { if (!deleting) setBatchDeleteOpen(open); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认批量删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除选中的 {selectedIds.size} 条追踪记录吗？删除后不可恢复，不影响会话和 Tokens 统计。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleting}
              onClick={(event) => {
                event.preventDefault();
                void handleBatchDelete();
              }}
            >
              {deleting ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog open={clearAllOpen} onOpenChange={(open) => { if (!deleting) setClearAllOpen(open); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认清空所有追踪记录</AlertDialogTitle>
            <AlertDialogDescription>
              确定要清空所有追踪记录吗？此操作不影响会话和 Tokens 统计，运行中的追踪记录将保留。删除后不可恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={deleting}
              onClick={(event) => {
                event.preventDefault();
                void handleClearAll();
              }}
            >
              {deleting ? "清空中..." : "确认清空"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <AlertDialog
        open={pendingRetentionPolicy !== null}
        onOpenChange={(open) => { if (!open && !retentionSaving) setPendingRetentionPolicy(null); }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认修改数据留存策略</AlertDialogTitle>
            <AlertDialogDescription>
              选择“{RETENTION_OPTIONS.find((option) => option.value === pendingRetentionPolicy)?.label}”后，后台将异步清理超出保留期限的追踪记录，清理后不可恢复。确定继续吗？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={retentionSaving}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={retentionSaving}
              onClick={(event) => {
                event.preventDefault();
                if (pendingRetentionPolicy) void saveRetentionPolicy(pendingRetentionPolicy);
              }}
            >
              {retentionSaving ? "保存中..." : "确认修改"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Sheet open={detail !== null || detailLoading} onOpenChange={(open) => { if (!open) setDetail(null); }}>
        <SheetContent className="overflow-y-auto px-4 pb-4 data-[side=right]:sm:max-w-5xl">
          {detailLoading && !detail ? (
            <div className="py-16 text-center text-muted-foreground">加载中...</div>
          ) : detail && (
            <>
              <SheetHeader>
                <SheetTitle className="flex items-center gap-2">
                  批次详情
                  {statusBadge(detail.status)}
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
                    <button
                      className="text-primary hover:underline"
                      onClick={() => navigate(`/?agentId=${detail.agentId}&conversationId=${detail.conversationId}`)}
                    >
                      {detail.conversationTitle ?? `会话#${detail.conversationId}`}
                    </button>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">耗时</div>
                    {formatDuration(detail)}
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">tokens</div>
                    <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{detail.inputTokens ?? "-"}</span>
                    {" / "}
                    <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{detail.outputTokens ?? "-"}</span>
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">开始时间</div>
                    {formatTs(detail.createdAt)}
                  </div>
                  <div>
                    <div className="text-xs text-muted-foreground">完成时间</div>
                    {formatTs(detail.completedAt)}
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
    </div>
  );
}
