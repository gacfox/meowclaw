import { useState, useEffect, useCallback } from "react";
import type { LlmDTO } from "@/types";
import { listLlms, createLlm, updateLlm, deleteLlm as apiDeleteLlm } from "@/services/llm";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Plus, Pencil, Trash2, Eye, EyeOff, Globe, Cpu, RefreshCw } from "lucide-react";
import { toast } from "sonner";

const CAPABILITY_OPTIONS = [
  { value: "vision", label: "Vision" },
  { value: "tool", label: "Tool Calling" },
  { value: "reasoning", label: "Reasoning" },
] as const;

type Capability = (typeof CAPABILITY_OPTIONS)[number]["value"];

interface LlmFormData {
  name: string;
  endpointUrl: string;
  sk: string;
  model: string;
  maxTokens: string;
  contextLength: string;
  temperature: string;
  capabilities: Capability[];
}

const emptyForm: LlmFormData = { name: "", endpointUrl: "", sk: "", model: "", maxTokens: "", contextLength: "", temperature: "", capabilities: [] };

function parseCapabilities(raw: string | null): Capability[] {
  if (!raw) return [];
  return raw.split(",").map((s) => s.trim()).filter((s): s is Capability => CAPABILITY_OPTIONS.some((o) => o.value === s));
}

function capabilitiesToString(caps: Capability[]): string {
  return caps.join(",");
}

export function LlmConfigPage() {
  const [llms, setLlms] = useState<LlmDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [visibleSks, setVisibleSks] = useState<Set<number>>(new Set());

  const fetchLlms = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listLlms();
      setLlms(list);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLlms();
  }, [fetchLlms]);

  const toggleSk = (id: number) => {
    setVisibleSks((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const maskSk = (sk: string | null) => {
    if (!sk) return "未设置";
    if (sk.length <= 8) return "****";
    return sk.slice(0, 4) + "****" + sk.slice(-4);
  };

  // Create / Edit dialog
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<LlmDTO | null>(null);
  const [form, setForm] = useState<LlmFormData>(emptyForm);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setDialogOpen(true);
  };

  const openEdit = (llm: LlmDTO) => {
    setEditing(llm);
    setForm({
      name: llm.name,
      endpointUrl: llm.endpointUrl,
      sk: llm.sk ?? "",
      model: llm.model,
      maxTokens: llm.maxTokens?.toString() ?? "",
      contextLength: llm.contextLength?.toString() ?? "",
      temperature: llm.temperature != null ? (llm.temperature / 100).toString() : "",
      capabilities: parseCapabilities(llm.capabilities),
    });
    setDialogOpen(true);
  };

  const toggleCapability = (cap: Capability) => {
    setForm((prev) => ({
      ...prev,
      capabilities: prev.capabilities.includes(cap)
        ? prev.capabilities.filter((c) => c !== cap)
        : [...prev.capabilities, cap],
    }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const data = {
        name: form.name,
        endpointUrl: form.endpointUrl,
        sk: form.sk || undefined,
        model: form.model,
        maxTokens: form.maxTokens ? parseInt(form.maxTokens) : undefined,
        contextLength: form.contextLength ? parseInt(form.contextLength) : undefined,
        temperature: form.temperature ? Math.round(parseFloat(form.temperature) * 100) : undefined,
        capabilities: form.capabilities.length > 0 ? capabilitiesToString(form.capabilities) : undefined,
      };
      if (editing) {
        await updateLlm(editing.id, data);
      } else {
        await createLlm(data);
      }
      await fetchLlms();
      setDialogOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<LlmDTO | null>(null);

  const openDelete = (llm: LlmDTO) => {
    setDeleting(llm);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteLlm(deleting.id);
      await fetchLlms();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const renderCapabilityBadges = (caps: string | null) => {
    if (!caps) return null;
    const list = caps.split(",").map((s) => s.trim()).filter(Boolean);
    return list.map((cap) => {
      const option = CAPABILITY_OPTIONS.find((o) => o.value === cap);
      return (
        <Badge key={cap} variant="outline" className="text-xs">
          {option?.label ?? cap}
        </Badge>
      );
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">LLM 配置</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            添加配置
          </Button>
          <Button variant="outline" size="icon" onClick={fetchLlms} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : llms.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">暂无配置，点击「添加配置」创建</div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {llms.map((llm) => (
            <Card key={llm.id}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
                <div className="flex items-center gap-2">
                  <Cpu className="size-4 text-muted-foreground" />
                  <CardTitle className="text-base">{llm.name}</CardTitle>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openEdit(llm)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openDelete(llm)}>
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{llm.model}</Badge>
                  {renderCapabilityBadges(llm.capabilities)}
                </div>
                <div className="flex items-center gap-1 text-muted-foreground">
                  <Globe className="size-3" />
                  <span className="truncate">{llm.endpointUrl}</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-muted-foreground">SK:</span>
                  <code className="text-xs">
                    {visibleSks.has(llm.id) ? (llm.sk ?? "未设置") : maskSk(llm.sk)}
                  </code>
                  <Button variant="ghost" size="icon-xs" onClick={() => toggleSk(llm.id)}>
                    {visibleSks.has(llm.id) ? <EyeOff className="size-3" /> : <Eye className="size-3" />}
                  </Button>
                </div>
                {(llm.maxTokens || llm.contextLength || llm.temperature != null) && (
                  <div className="flex gap-3 text-xs text-muted-foreground">
                    {llm.contextLength && <span>上下文长度: {llm.contextLength}</span>}
                    {llm.maxTokens && <span>最大输出长度: {llm.maxTokens}</span>}
                    {llm.temperature != null && <span>温度: {(llm.temperature / 100).toFixed(2)}</span>}
                  </div>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑配置" : "添加配置"}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>配置名称</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label>端点 URL</Label>
              <Input value={form.endpointUrl} onChange={(e) => setForm({ ...form, endpointUrl: e.target.value })} placeholder="https://api.openai.com/v1/chat/completions" />
            </div>
            <div className="flex flex-col gap-2">
              <Label>API 密钥 (SK)</Label>
              <Input type="password" value={form.sk} onChange={(e) => setForm({ ...form, sk: e.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label>模型</Label>
              <Input value={form.model} onChange={(e) => setForm({ ...form, model: e.target.value })} placeholder="gpt-4o" />
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="flex flex-col gap-2">
                <Label>上下文长度</Label>
                <Input type="number" value={form.contextLength} onChange={(e) => setForm({ ...form, contextLength: e.target.value })} />
              </div>
              <div className="flex flex-col gap-2">
                <Label>最大输出长度</Label>
                <Input type="number" value={form.maxTokens} onChange={(e) => setForm({ ...form, maxTokens: e.target.value })} />
              </div>
              <div className="flex flex-col gap-2">
                <Label>温度</Label>
                <Input type="number" step="0.01" min="0" max="2" value={form.temperature} onChange={(e) => setForm({ ...form, temperature: e.target.value })} placeholder="0.70" />
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Label>能力标签</Label>
              <div className="flex flex-wrap gap-2">
                {CAPABILITY_OPTIONS.map((opt) => (
                  <Button
                    key={opt.value}
                    type="button"
                    size="sm"
                    variant={form.capabilities.includes(opt.value) ? "default" : "outline"}
                    onClick={() => toggleCapability(opt.value)}
                  >
                    {opt.label}
                  </Button>
                ))}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>取消</Button>
            <Button onClick={handleSave} disabled={saving || !form.name || !form.endpointUrl || !form.model}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除配置「{deleting?.name}」吗？此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
