import { useState, useEffect, useCallback, useMemo } from "react";
import { toast } from "sonner";
import { listAgents } from "@/services/agent";
import { listMemories, createMemory, updateMemory, deleteMemory } from "@/services/memory";
import type { AgentDTO, MemoryNodeDTO, PageResult } from "@/types";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from "@/components/ui/table";
import {
  Pagination, PaginationContent, PaginationEllipsis, PaginationItem, PaginationLink, PaginationNext, PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Pencil, Plus, RotateCcw, Search, Trash2 } from "lucide-react";

const DEFAULT_SIZE = 20;
const ALL_VALUE = "ALL";

const TYPE_OPTIONS = [
  { value: ALL_VALUE, label: "全部" },
  { value: "fact", label: "事实" },
  { value: "preference", label: "偏好" },
  { value: "rule", label: "规则" },
];

function typeBadgeVariant(type: string): "default" | "secondary" | "outline" {
  if (type === "preference") return "secondary";
  if (type === "rule") return "outline";
  return "default";
}

function typeLabel(type: string): string {
  return TYPE_OPTIONS.find((o) => o.value === type)?.label ?? type;
}

function formatTimestamp(ts: number): string {
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function MemoryPage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [agentId, setAgentId] = useState<string>("");
  const [type, setType] = useState<string>(ALL_VALUE);
  const [keyword, setKeyword] = useState("");
  const [appliedType, setAppliedType] = useState<string>(ALL_VALUE);
  const [appliedKeyword, setAppliedKeyword] = useState("");
  const [data, setData] = useState<PageResult<MemoryNodeDTO>>({ list: [], total: 0, current: 1, pageSize: DEFAULT_SIZE });
  const [loading, setLoading] = useState(false);

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

  const load = useCallback(async (page: number, currentAgentId: string, currentType: string, currentKeyword: string) => {
    if (!currentAgentId) return;
    setLoading(true);
    try {
      const result = await listMemories({
        agentId: Number(currentAgentId),
        type: currentType === ALL_VALUE ? undefined : currentType,
        keyword: currentKeyword.trim() || undefined,
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

  useEffect(() => {
    load(1, agentId, appliedType, appliedKeyword);
  }, [agentId, appliedType, appliedKeyword, load]);

  const handleQuery = () => {
    setAppliedType(type);
    setAppliedKeyword(keyword);
  };

  const handleReset = () => {
    setType(ALL_VALUE);
    setKeyword("");
    setAppliedType(ALL_VALUE);
    setAppliedKeyword("");
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
      } else {
        await createMemory({ agentId: Number(agentId), type: formType, content: formContent.trim() });
        toast.success("已添加");
        setCreateOpen(false);
      }
      load(data.current, agentId, appliedType, appliedKeyword);
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
      load(data.current, agentId, appliedType, appliedKeyword);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
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
        <Button onClick={openCreate} disabled={!agentId}>
          <Plus className="mr-1 size-4" />
          添加记忆
        </Button>
      </div>

      <div className="flex flex-wrap items-end gap-3 rounded-lg border bg-card p-4">
        <div className="flex flex-col gap-1.5 min-w-[160px]">
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

        <div className="flex flex-col gap-1.5 min-w-[120px]">
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

        <div className="flex flex-col gap-1.5 min-w-[200px] flex-1">
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
      </div>

      <div className="text-sm text-muted-foreground">共 {data.total} 条</div>

      <div className="rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>内容</TableHead>
              <TableHead className="w-24">类型</TableHead>
              <TableHead className="w-48">相关实体</TableHead>
              <TableHead className="w-36">更新时间</TableHead>
              <TableHead className="w-24 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading && data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-32 text-center text-muted-foreground">
                  加载中...
                </TableCell>
              </TableRow>
            ) : data.list.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="h-32 text-center text-muted-foreground">
                  {agentId ? "暂无记忆" : "请先创建智能体"}
                </TableCell>
              </TableRow>
            ) : (
              data.list.map((node) => (
                <TableRow key={node.id}>
                  <TableCell className="max-w-md">
                    <span className="line-clamp-2 whitespace-pre-wrap" title={node.content}>{node.content}</span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={typeBadgeVariant(node.type)}>{typeLabel(node.type)}</Badge>
                  </TableCell>
                  <TableCell className="max-w-48">
                    <div className="flex flex-wrap gap-1">
                      {(node.entities ?? []).map((entity) => (
                        <Badge key={entity.id} variant="outline" className="text-xs">{entity.name}</Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatTimestamp(node.updatedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon-sm" onClick={() => openEdit(node)} title="编辑">
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => setDeleteTarget(node)} title="删除">
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
                onClick={() => data.current > 1 && load(data.current - 1, agentId, appliedType, appliedKeyword)}
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
                    onClick={() => load(page, agentId, appliedType, appliedKeyword)}
                  >
                    {page}
                  </PaginationLink>
                )}
              </PaginationItem>
            ))}
            <PaginationItem>
              <PaginationNext
                onClick={() => data.current < totalPages && load(data.current + 1, agentId, appliedType, appliedKeyword)}
                className={data.current >= totalPages ? "pointer-events-none opacity-50" : ""}
                text="下一页"
              />
            </PaginationItem>
          </PaginationContent>
        </Pagination>
      )}

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
