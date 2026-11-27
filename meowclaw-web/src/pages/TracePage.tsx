import { useState, useEffect, useCallback, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { endOfDay, startOfDay } from "date-fns";
import { toast } from "sonner";
import { listAgents } from "@/services/agent";
import {
  batchDeleteTraces,
  deleteAllTraces,
  deleteTrace,
  getTraceDetail,
  listTraces,
  type TraceQuery,
} from "@/services/trace";
import type { AgentDTO, PageResult, TraceDetail, TraceItem } from "@/types";
import { TraceDetailSheet } from "@/components/trace/TraceDetailSheet";
import { TraceFilters, type TraceFilterValues } from "@/components/trace/TraceFilters";
import { formatTraceDuration, formatTraceTimestamp, traceStatusBadge } from "@/components/trace/traceUtils";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious,
} from "@/components/ui/pagination";
import { ArrowDown, ArrowUp, RefreshCw, Trash2 } from "lucide-react";

const DEFAULT_SIZE = 20;
const ALL_VALUE = "ALL";

const defaultFilters: TraceFilterValues = { agentId: ALL_VALUE, status: ALL_VALUE, keyword: "" };

export function TracePage() {
  const navigate = useNavigate();
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [filters, setFilters] = useState<TraceFilterValues>(defaultFilters);
  const [appliedFilters, setAppliedFilters] = useState<TraceFilterValues>(defaultFilters);
  const [data, setData] = useState<PageResult<TraceItem>>({ list: [], total: 0, current: 1, pageSize: DEFAULT_SIZE });
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<TraceDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteTarget, setDeleteTarget] = useState<TraceItem | null>(null);
  const [batchDeleteOpen, setBatchDeleteOpen] = useState(false);
  const [clearAllOpen, setClearAllOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    listAgents().then(setAgents).catch(() => {});
  }, []);

  const load = useCallback(async (page: number, currentFilters: TraceFilterValues) => {
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

      <TraceFilters
        agents={agents}
        filters={filters}
        onChange={setFilters}
        onQuery={handleQuery}
        onReset={handleReset}
      />

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
                  <TableCell className="text-muted-foreground">{formatTraceTimestamp(item.createdAt)}</TableCell>
                  <TableCell className="max-w-28 truncate">{item.agentName ?? "-"}</TableCell>
                  <TableCell className="max-w-40 truncate">
                    {item.conversationTitle ?? `会话#${item.conversationId}`}
                  </TableCell>
                  <TableCell className="max-w-md">
                    <span className="line-clamp-1" title={item.userContent}>{item.userContent}</span>
                  </TableCell>
                  <TableCell>{traceStatusBadge(item.status)}</TableCell>
                  <TableCell>{item.llmCallCount}</TableCell>
                  <TableCell className="text-xs text-muted-foreground">
                    <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{item.inputTokens ?? "-"}</span>
                    {" / "}
                    <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{item.outputTokens ?? "-"}</span>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{formatTraceDuration(item)}</TableCell>
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

      <TraceDetailSheet
        detail={detail}
        loading={detailLoading}
        onClose={() => setDetail(null)}
        onOpenConversation={() => {
          if (detail) navigate(`/?agentId=${detail.agentId}&conversationId=${detail.conversationId}`);
        }}
      />
    </div>
  );
}
