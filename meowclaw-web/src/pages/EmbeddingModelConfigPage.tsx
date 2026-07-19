import { useState, useEffect, useCallback } from "react";
import type { EmbeddingModelDTO, EmbeddingModelTestResultDTO } from "@/types";
import {
  listEmbeddingModels,
  createEmbeddingModel,
  updateEmbeddingModel,
  deleteEmbeddingModel as apiDeleteEmbeddingModel,
  testEmbeddingModel,
} from "@/services/embeddingModel";
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
import { Plus, Pencil, Trash2, Copy, Eye, EyeOff, Globe, Database, RefreshCw, Play } from "lucide-react";
import { toast } from "sonner";

interface EmbeddingModelFormData {
  name: string;
  endpointUrl: string;
  sk: string;
  model: string;
  dimensions: string;
}

const emptyForm: EmbeddingModelFormData = {
  name: "",
  endpointUrl: "",
  sk: "",
  model: "",
  dimensions: "",
};

export function EmbeddingModelConfigPage() {
  const [models, setModels] = useState<EmbeddingModelDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [visibleSks, setVisibleSks] = useState<Set<number>>(new Set());

  const fetchModels = useCallback(async () => {
    setLoading(true);
    try {
      const list = await listEmbeddingModels();
      setModels(list);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

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
  const [editing, setEditing] = useState<EmbeddingModelDTO | null>(null);
  const [form, setForm] = useState<EmbeddingModelFormData>(emptyForm);
  const [saving, setSaving] = useState(false);

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<EmbeddingModelTestResultDTO | null>(null);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setTestResult(null);
    setDialogOpen(true);
  };

  const openEdit = (model: EmbeddingModelDTO) => {
    setEditing(model);
    setForm({
      name: model.name,
      endpointUrl: model.endpointUrl,
      sk: model.sk ?? "",
      model: model.model,
      dimensions: model.dimensions?.toString() ?? "",
    });
    setTestResult(null);
    setDialogOpen(true);
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testEmbeddingModel({
        endpointUrl: form.endpointUrl,
        sk: form.sk || undefined,
        model: form.model,
        dimensions: parseInt(form.dimensions),
      });
      setTestResult(result);
    } catch (err) {
      setTestResult({ success: false, dimensions: null, errorMessage: err instanceof Error ? err.message : "请求失败" });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const data = {
        name: form.name,
        endpointUrl: form.endpointUrl,
        sk: form.sk || undefined,
        model: form.model,
        dimensions: parseInt(form.dimensions),
      };
      if (editing) {
        await updateEmbeddingModel(editing.id, data);
      } else {
        await createEmbeddingModel(data);
      }
      await fetchModels();
      setDialogOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<EmbeddingModelDTO | null>(null);

  const openDelete = (model: EmbeddingModelDTO) => {
    setDeleting(model);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteEmbeddingModel(deleting.id);
      await fetchModels();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  // Copy dialog
  const [copyDialogOpen, setCopyDialogOpen] = useState(false);
  const [copyingModel, setCopyingModel] = useState<EmbeddingModelDTO | null>(null);
  const [copyName, setCopyName] = useState("");
  const [copySaving, setCopySaving] = useState(false);

  const openCopy = (model: EmbeddingModelDTO) => {
    setCopyingModel(model);
    setCopyName(`${model.name}(复制)`);
    setCopyDialogOpen(true);
  };

  const handleCopy = async () => {
    if (!copyingModel || !copyName.trim()) return;
    setCopySaving(true);
    try {
      await createEmbeddingModel({
        name: copyName.trim(),
        endpointUrl: copyingModel.endpointUrl,
        sk: copyingModel.sk ?? undefined,
        model: copyingModel.model,
        dimensions: copyingModel.dimensions,
      });
      await fetchModels();
      setCopyDialogOpen(false);
      toast.success("复制成功");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "复制失败");
    } finally {
      setCopySaving(false);
    }
  };

  const canSave =
    form.name && form.endpointUrl && form.model && form.dimensions && !isNaN(parseInt(form.dimensions));

  const canTest =
    form.endpointUrl && form.model && form.dimensions && !isNaN(parseInt(form.dimensions));

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">向量嵌入模型</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            添加配置
          </Button>
          <Button variant="outline" size="icon" onClick={fetchModels} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : models.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">
          暂无配置，点击「添加配置」创建
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {models.map((model) => (
            <Card key={model.id}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
                <div className="flex items-center gap-2">
                  <Database className="size-4 text-muted-foreground" />
                  <CardTitle className="text-base">{model.name}</CardTitle>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openEdit(model)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openCopy(model)} title="复制">
                    <Copy className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openDelete(model)}>
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                <div className="flex items-center gap-2">
                  <Badge variant="secondary">{model.model}</Badge>
                  <Badge variant="outline" className="text-xs">{model.dimensions} 维</Badge>
                </div>
                <div className="flex items-center gap-1 text-muted-foreground">
                  <Globe className="size-3" />
                  <span className="truncate">{model.endpointUrl}</span>
                </div>
                <div className="flex items-center gap-1">
                  <span className="text-muted-foreground">SK:</span>
                  <code className="text-xs">
                    {visibleSks.has(model.id) ? (model.sk ?? "未设置") : maskSk(model.sk)}
                  </code>
                  <Button variant="ghost" size="icon-xs" onClick={() => toggleSk(model.id)}>
                    {visibleSks.has(model.id) ? <EyeOff className="size-3" /> : <Eye className="size-3" />}
                  </Button>
                </div>
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
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>端点 URL</Label>
              <Input
                value={form.endpointUrl}
                onChange={(e) => setForm({ ...form, endpointUrl: e.target.value })}
                placeholder="https://api.openai.com/v1/embeddings"
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>API 密钥 (SK)</Label>
              <Input
                type="password"
                value={form.sk}
                onChange={(e) => setForm({ ...form, sk: e.target.value })}
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>模型</Label>
              <Input
                value={form.model}
                onChange={(e) => setForm({ ...form, model: e.target.value })}
                placeholder="text-embedding-3-small"
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>向量维度</Label>
              <Input
                type="number"
                value={form.dimensions}
                onChange={(e) => setForm({ ...form, dimensions: e.target.value })}
                placeholder="1024"
              />
            </div>

            <div className="flex items-center gap-2">
              <Button variant="outline" type="button" onClick={handleTest} disabled={testing || !canTest}>
                {testing ? "测试中..." : "测试模型"}
                <Play className="ml-1 size-4" />
              </Button>
              {testResult && (
                <span className={testResult.success ? "text-sm text-primary" : "text-sm text-destructive"}>
                  {testResult.success
                    ? `测试成功，返回维度 ${testResult.dimensions}`
                    : `测试失败: ${testResult.errorMessage}`}
                </span>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={handleSave} disabled={saving || !canSave}>
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

      {/* Copy Dialog */}
      <Dialog open={copyDialogOpen} onOpenChange={setCopyDialogOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>复制配置</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>新配置名称</Label>
              <Input
                value={copyName}
                onChange={(e) => setCopyName(e.target.value)}
                placeholder="请输入新配置名称"
                autoFocus
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCopyDialogOpen(false)} disabled={copySaving}>
              取消
            </Button>
            <Button onClick={handleCopy} disabled={copySaving || !copyName.trim()}>
              {copySaving ? "复制中..." : "确认"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
