import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import * as echarts from "echarts";
import type { EChartsOption } from "echarts";
import { toast } from "sonner";
import { listAgents } from "@/services/agent";
import { createMemory, deleteMemory, getMemoryGraph, listMemories, listMemoryEntities, recallPreview, updateMemory } from "@/services/memory";
import type { AgentDTO, MemoryEntityWithCount, MemoryGraph, MemoryNodeDTO, PageResult } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Sheet, SheetContent, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { FlaskConical, Pencil, Plus, RotateCcw, Search, Trash2 } from "lucide-react";

const DEFAULT_SIZE = 20;
const ALL_VALUE = "ALL";

const TYPE_OPTIONS = [
  { value: ALL_VALUE, label: "全部" },
  { value: "fact", label: "事实" },
  { value: "preference", label: "偏好" },
  { value: "rule", label: "规则" },
];

const TYPE_COLORS: Record<string, string> = {
  fact: "#3b82f6",
  preference: "#22c55e",
  rule: "#f59e0b",
};
const ENTITY_COLOR = "#a855f7";

function typeBadgeVariant(type: string): "default" | "secondary" | "outline" {
  if (type === "preference") return "secondary";
  if (type === "rule") return "outline";
  return "default";
}

function typeLabel(type: string): string {
  return TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type;
}

function formatTimestamp(ts: number | null): string {
  if (ts == null || ts <= 0) return "-";
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function truncateLabel(text: string, max: number): string {
  const flat = text.replace(/\s+/g, " ").trim();
  return flat.length > max ? flat.slice(0, max) + "…" : flat;
}

function EntityChips({ node }: { node: MemoryNodeDTO }) {
  if (!node.entities || node.entities.length === 0) return null;
  return (
    <div className="flex flex-wrap gap-1">
      {node.entities.map((entity) => (
        <Badge key={entity.id} variant="outline" className="text-xs">{entity.name}</Badge>
      ))}
    </div>
  );
}

function MemoryCard({ node, onClick }: { node: MemoryNodeDTO; onClick: () => void }) {
  return (
    <Card className="cursor-pointer transition-shadow hover:shadow-md" onClick={onClick}>
      <CardContent className="flex flex-col gap-2 p-4">
        <div className="flex items-center justify-between">
          <Badge variant={typeBadgeVariant(node.type)}>{typeLabel(node.type)}</Badge>
          <span className="text-xs text-muted-foreground">{formatTimestamp(node.updatedAt)}</span>
        </div>
        <p className="line-clamp-3 whitespace-pre-wrap text-sm">{node.content}</p>
        <EntityChips node={node} />
      </CardContent>
    </Card>
  );
}

function MemoryGraphView({ graph, onMemoryClick }: { graph: MemoryGraph; onMemoryClick: (memoryId: number) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const chartRef = useRef<echarts.ECharts | null>(null);
  const onMemoryClickRef = useRef(onMemoryClick);
  onMemoryClickRef.current = onMemoryClick;

  useEffect(() => {
    if (!containerRef.current) return;
    const chart = echarts.init(containerRef.current);
    chartRef.current = chart;
    chart.on("click", (params) => {
      const data = params.data as { memoryId?: number } | undefined;
      if (params.dataType === "node" && data?.memoryId != null) {
        onMemoryClickRef.current(data.memoryId);
      }
    });
    const resizeObserver = new ResizeObserver(() => chart.resize());
    resizeObserver.observe(containerRef.current);
    return () => {
      resizeObserver.disconnect();
      chart.dispose();
      chartRef.current = null;
    };
  }, []);

  useEffect(() => {
    const chart = chartRef.current;
    if (!chart) return;
    const nodes = [
      ...graph.memories.map((m) => ({
        id: `m-${m.id}`,
        name: truncateLabel(m.content, 12),
        memoryId: m.id,
        category: typeLabel(m.type),
        symbolSize: 26,
      })),
      ...graph.entities.map((e) => ({
        id: `e-${e.id}`,
        name: truncateLabel(e.name, 12),
        category: "实体",
        symbol: "diamond",
        symbolSize: 20,
      })),
    ];
    const links = graph.relations.map((r) => ({
      source: `m-${r.memoryId}`,
      target: `e-${r.entityId}`,
      value: r.description,
    }));
    const option: EChartsOption = {
      tooltip: {
        formatter: (params: unknown) => {
          const p = params as { dataType: string; data?: { value?: string }; name: string };
          if (p.dataType === "edge") return p.data?.value ?? "";
          return p.name;
        },
      },
      legend: {
        data: ["事实", "偏好", "规则", "实体"],
        bottom: 0,
        textStyle: { fontSize: 12 },
      },
      series: [{
        type: "graph",
        layout: "force",
        roam: true,
        draggable: true,
        force: { repulsion: 220, edgeLength: 100 },
        label: { show: true, fontSize: 10 },
        categories: [
          { name: "事实", itemStyle: { color: TYPE_COLORS.fact } },
          { name: "偏好", itemStyle: { color: TYPE_COLORS.preference } },
          { name: "规则", itemStyle: { color: TYPE_COLORS.rule } },
          { name: "实体", itemStyle: { color: ENTITY_COLOR } },
        ],
        data: nodes,
        links,
        lineStyle: { color: "source", opacity: 0.4, curveness: 0.1 },
        emphasis: { focus: "adjacency" },
      }] as EChartsOption["series"],
    };
    chart.setOption(option, true);
  }, [graph]);

  return (
    <div className="relative">
      <div ref={containerRef} className="h-[600px] w-full rounded-lg border" />
      {graph.memories.length === 0 && (
        <div className="absolute inset-0 flex items-center justify-center text-muted-foreground">暂无记忆数据</div>
      )}
    </div>
  );
}

export function MemoryPage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [agentId, setAgentId] = useState<string>("");
  const [view, setView] = useState<"list" | "graph">("list");
  const [type, setType] = useState<string>(ALL_VALUE);
  const [keyword, setKeyword] = useState("");
  const [appliedType, setAppliedType] = useState<string>(ALL_VALUE);
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [selectedEntityId, setSelectedEntityId] = useState<number | null>(null);
  const [data, setData] = useState<PageResult<MemoryNodeDTO>>({ list: [], total: 0, current: 1, pageSize: DEFAULT_SIZE });
  const [entities, setEntities] = useState<MemoryEntityWithCount[]>([]);
  const [graph, setGraph] = useState<MemoryGraph | null>(null);
  const [loading, setLoading] = useState(false);

  const [detail, setDetail] = useState<MemoryNodeDTO | null>(null);
  const [recallOpen, setRecallOpen] = useState(false);
  const [recallQuery, setRecallQuery] = useState("");
  const [recallResults, setRecallResults] = useState<MemoryNodeDTO[] | null>(null);
  const [recallLoading, setRecallLoading] = useState(false);

  const [editTarget, setEditTarget] = useState<MemoryNodeDTO | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [formType, setFormType] = useState("fact");
  const [formContent, setFormContent] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<MemoryNodeDTO | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    listAgents().then((list) => {
      setAgents(list);
      if (list.length > 0) setAgentId((prev) => prev || String(list[0].id));
    }).catch(() => {});
  }, []);

  const load = useCallback(async (page: number, currentAgentId: string, currentType: string, currentKeyword: string, entityId: number | null) => {
    if (!currentAgentId) return;
    setLoading(true);
    try {
      const result = await listMemories({
        agentId: Number(currentAgentId),
        type: currentType === ALL_VALUE ? undefined : currentType,
        keyword: currentKeyword.trim() || undefined,
        entityId: entityId ?? undefined,
        page,
        size: DEFAULT_SIZE,
      });
      setData(result);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载失败");
    } finally {
      setLoading(false);
    }
  }, []);

  const loadEntities = useCallback((currentAgentId: string) => {
    if (!currentAgentId) return;
    listMemoryEntities(Number(currentAgentId)).then(setEntities).catch(() => {});
  }, []);

  const loadGraph = useCallback((currentAgentId: string) => {
    if (!currentAgentId) return;
    getMemoryGraph(Number(currentAgentId)).then(setGraph).catch((err) => {
      toast.error(err instanceof Error ? err.message : "图谱加载失败");
    });
  }, []);

  useEffect(() => {
    setSelectedEntityId(null);
    loadEntities(agentId);
  }, [agentId, loadEntities]);

  useEffect(() => {
    load(1, agentId, appliedType, appliedKeyword, selectedEntityId);
  }, [agentId, appliedType, appliedKeyword, selectedEntityId, load]);

  useEffect(() => {
    if (view === "graph" && agentId) {
      loadGraph(agentId);
    }
  }, [view, agentId, loadGraph]);

  const refreshAll = () => {
    load(data.current, agentId, appliedType, appliedKeyword, selectedEntityId);
    loadEntities(agentId);
    if (view === "graph") loadGraph(agentId);
  };

  const handleQuery = () => {
    setAppliedType(type);
    setAppliedKeyword(keyword);
  };

  const handleReset = () => {
    setType(ALL_VALUE);
    setKeyword("");
    setAppliedType(ALL_VALUE);
    setAppliedKeyword("");
    setSelectedEntityId(null);
  };

  const openCreate = () => {
    setFormType("fact");
    setFormContent("");
    setCreateOpen(true);
  };

  const openEdit = (node: MemoryNodeDTO) => {
    setFormType(node.type);
    setFormContent(node.content);
    setEditTarget(node);
  };

  const handleSave = async () => {
    if (!formContent.trim()) {
      toast.error("记忆内容不能为空");
      return;
    }
    setSaving(true);
    try {
      if (editTarget) {
        await updateMemory(editTarget.id, { type: formType, content: formContent.trim() });
        toast.success("已更新");
        setEditTarget(null);
        setDetail(null);
      } else {
        await createMemory({ agentId: Number(agentId), type: formType, content: formContent.trim() });
        toast.success("已添加");
        setCreateOpen(false);
      }
      refreshAll();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await deleteMemory(deleteTarget.id);
      toast.success("已删除");
      setDeleteTarget(null);
      setDetail(null);
      refreshAll();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  const runRecallPreview = async () => {
    if (!agentId || !recallQuery.trim()) return;
    setRecallLoading(true);
    try {
      setRecallResults(await recallPreview(Number(agentId), recallQuery.trim(), 10));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "召回失败");
    } finally {
      setRecallLoading(false);
    }
  };

  const openGraphMemoryDetail = (memoryId: number) => {
    if (!graph) return;
    const memory = graph.memories.find((m) => m.id === memoryId);
    if (!memory) return;
    const relations = graph.relations.filter((r) => r.memoryId === memoryId);
    setDetail({
      id: memory.id,
      type: memory.type,
      content: memory.content,
      createdAt: 0,
      updatedAt: 0,
      lastAccessedAt: null,
      entities: relations.flatMap((r) => {
        const entity = graph.entities.find((e) => e.id === r.entityId);
        return entity ? [{ id: entity.id, name: entity.name }] : [];
      }),
      relations: relations.map((r) => ({ id: 0, entityId: r.entityId, description: r.description })),
    });
  };

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

  const formOpen = createOpen || editTarget !== null;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">记忆</h1>
        <div className="flex items-center gap-2">
          <Tabs value={view} onValueChange={(v) => setView(v as "list" | "graph")}>
            <TabsList>
              <TabsTrigger value="list">列表</TabsTrigger>
              <TabsTrigger value="graph">图谱</TabsTrigger>
            </TabsList>
          </Tabs>
          <Button variant="outline" onClick={() => setRecallOpen(true)} disabled={!agentId}>
            <FlaskConical className="mr-1 size-4" />
            召回测试
          </Button>
          <Button onClick={openCreate} disabled={!agentId}>
            <Plus className="mr-1 size-4" />
            添加记忆
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4">
        <div className="flex min-w-[160px] flex-col gap-1.5">
          <Label className="text-xs text-muted-foreground">智能体</Label>
          <Select value={agentId} onValueChange={setAgentId}>
            <SelectTrigger className="w-[160px]">
              <SelectValue placeholder="选择智能体" />
            </SelectTrigger>
            <SelectContent>
              {agents.map((agent) => (
                <SelectItem key={agent.id} value={String(agent.id)}>{agent.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        {view === "list" && (
          <>
            <div className="flex min-w-[120px] flex-col gap-1.5">
              <Label className="text-xs text-muted-foreground">类型</Label>
              <Select value={type} onValueChange={setType}>
                <SelectTrigger className="w-[120px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TYPE_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div className="flex min-w-[200px] flex-1 flex-col gap-1.5">
              <Label className="text-xs text-muted-foreground">关键字</Label>
              <div className="relative">
                <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
                <Input
                  placeholder="搜索记忆内容"
                  className="pl-9"
                  value={keyword}
                  onChange={(e) => setKeyword(e.target.value)}
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
          </>
        )}
      </div>

      {view === "list" ? (
        <div className="flex items-start gap-4">
          <Card className="w-56 shrink-0 self-start">
            <CardContent className="max-h-[640px] overflow-y-auto p-2">
              <button
                className={`flex w-full items-center justify-between rounded px-2 py-1.5 text-sm hover:bg-accent ${selectedEntityId === null ? "bg-accent font-medium" : ""}`}
                onClick={() => setSelectedEntityId(null)}
              >
                全部
              </button>
              {entities.map((entity) => (
                <button
                  key={entity.id}
                  className={`flex w-full items-center justify-between gap-2 rounded px-2 py-1.5 text-sm hover:bg-accent ${selectedEntityId === entity.id ? "bg-accent font-medium" : ""}`}
                  onClick={() => setSelectedEntityId(selectedEntityId === entity.id ? null : entity.id)}
                  title={entity.name}
                >
                  <span className="truncate">{entity.name}</span>
                  <Badge variant="secondary" className="shrink-0 text-xs">{entity.memoryCount}</Badge>
                </button>
              ))}
              {entities.length === 0 && (
                <div className="px-2 py-4 text-center text-xs text-muted-foreground">暂无实体</div>
              )}
            </CardContent>
          </Card>

          <div className="flex flex-1 flex-col gap-4">
            <div className="text-sm text-muted-foreground">共 {data.total} 条</div>
            {loading && data.list.length === 0 ? (
              <div className="py-16 text-center text-muted-foreground">加载中...</div>
            ) : data.list.length === 0 ? (
              <div className="py-16 text-center text-muted-foreground">
                {agentId ? "暂无记忆" : "请先创建智能体"}
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {data.list.map((node) => (
                  <MemoryCard key={node.id} node={node} onClick={() => setDetail(node)} />
                ))}
              </div>
            )}

            {totalPages > 1 && (
              <Pagination>
                <PaginationContent>
                  <PaginationItem>
                    <PaginationPrevious
                      onClick={() => data.current > 1 && load(data.current - 1, agentId, appliedType, appliedKeyword, selectedEntityId)}
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
                          onClick={() => load(page, agentId, appliedType, appliedKeyword, selectedEntityId)}
                        >
                          {page}
                        </PaginationLink>
                      )}
                    </PaginationItem>
                  ))}
                  <PaginationItem>
                    <PaginationNext
                      onClick={() => data.current < totalPages && load(data.current + 1, agentId, appliedType, appliedKeyword, selectedEntityId)}
                      className={data.current >= totalPages ? "pointer-events-none opacity-50" : ""}
                      text="下一页"
                    />
                  </PaginationItem>
                </PaginationContent>
              </Pagination>
            )}
          </div>
        </div>
      ) : (
        graph && <MemoryGraphView graph={graph} onMemoryClick={openGraphMemoryDetail} />
      )}

      <Sheet open={detail !== null} onOpenChange={(open) => { if (!open) setDetail(null); }}>
        <SheetContent className="overflow-y-auto sm:max-w-lg">
          {detail && (
            <>
              <SheetHeader>
                <SheetTitle className="flex items-center gap-2">
                  记忆详情
                  <Badge variant={typeBadgeVariant(detail.type)}>{typeLabel(detail.type)}</Badge>
                </SheetTitle>
              </SheetHeader>
              <div className="flex flex-col gap-5 px-4 pb-4">
                <div>
                  <div className="mb-1 text-xs text-muted-foreground">内容</div>
                  <p className="whitespace-pre-wrap rounded-md bg-muted p-3 text-sm">{detail.content}</p>
                </div>
                {detail.relations && detail.relations.length > 0 && (
                  <div>
                    <div className="mb-1 text-xs text-muted-foreground">实体关系</div>
                    <div className="flex flex-col gap-2">
                      {detail.relations.map((relation, i) => {
                        const name = detail.entities?.find((e) => e.id === relation.entityId)?.name ?? `#${relation.entityId}`;
                        return (
                          <div key={relation.id || i} className="flex items-start gap-2 text-sm">
                            <Badge variant="outline" className="shrink-0">{name}</Badge>
                            <span className="text-muted-foreground">{relation.description}</span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
                {detail.createdAt > 0 && (
                  <div className="grid grid-cols-3 gap-2 text-sm">
                    <div>
                      <div className="text-xs text-muted-foreground">创建时间</div>
                      {formatTimestamp(detail.createdAt)}
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">更新时间</div>
                      {formatTimestamp(detail.updatedAt)}
                    </div>
                    <div>
                      <div className="text-xs text-muted-foreground">最近召回</div>
                      {formatTimestamp(detail.lastAccessedAt)}
                    </div>
                  </div>
                )}
                <div className="flex gap-2 border-t pt-4">
                  <Button variant="outline" size="sm" onClick={() => openEdit(detail)}>
                    <Pencil className="mr-1 size-4" />
                    编辑
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => setDeleteTarget(detail)}>
                    <Trash2 className="mr-1 size-4 text-destructive" />
                    删除
                  </Button>
                </div>
              </div>
            </>
          )}
        </SheetContent>
      </Sheet>

      <Dialog open={recallOpen} onOpenChange={setRecallOpen}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>召回测试</DialogTitle>
            <DialogDescription>
              预览当前智能体对输入文本实际召回的记忆，测试不会更新记忆的最近召回时间
            </DialogDescription>
          </DialogHeader>
          <div className="flex gap-2">
            <Input
              placeholder="输入查询文本..."
              value={recallQuery}
              onChange={(e) => setRecallQuery(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && runRecallPreview()}
            />
            <Button onClick={runRecallPreview} disabled={recallLoading || !recallQuery.trim()}>
              {recallLoading ? "召回中..." : "测试"}
            </Button>
          </div>
          <div className="flex max-h-[50vh] flex-col gap-2 overflow-y-auto">
            {recallResults === null ? (
              <div className="py-8 text-center text-sm text-muted-foreground">输入文本后点击测试</div>
            ) : recallResults.length === 0 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">无召回结果</div>
            ) : (
              recallResults.map((node) => (
                <Card key={node.id}>
                  <CardContent className="flex flex-col gap-2 p-3">
                    <div className="flex items-center justify-between">
                      <Badge variant={typeBadgeVariant(node.type)}>{typeLabel(node.type)}</Badge>
                      <span className="text-xs text-muted-foreground">id: {node.id}</span>
                    </div>
                    <p className="whitespace-pre-wrap text-sm">{node.content}</p>
                    <EntityChips node={node} />
                  </CardContent>
                </Card>
              ))
            )}
          </div>
        </DialogContent>
      </Dialog>

      <Dialog open={formOpen} onOpenChange={(open) => { if (!open) { setCreateOpen(false); setEditTarget(null); } }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editTarget ? "编辑记忆" : "添加记忆"}</DialogTitle>
            <DialogDescription>
              手动维护的记忆内容将原样保存，不经过模型提炼。
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label>类型</Label>
              <Select value={formType} onValueChange={setFormType}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TYPE_OPTIONS.filter((o) => o.value !== ALL_VALUE).map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-1.5">
              <Label>内容</Label>
              <Textarea
                rows={5}
                placeholder="输入记忆内容"
                value={formContent}
                onChange={(e) => setFormContent(e.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setCreateOpen(false); setEditTarget(null); }} disabled={saving}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除记忆</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除这条记忆吗？关联的实体关系将一并删除，此操作不可恢复。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={deleting}>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} disabled={deleting}>
              {deleting ? "删除中..." : "确认删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
