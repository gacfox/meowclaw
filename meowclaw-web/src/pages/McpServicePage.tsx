import { useState, useEffect, useCallback } from "react";
import type { McpProtocol, McpServiceDTO, McpTestResultDTO } from "@/types";
import {
  listMcpServices,
  createMcpService,
  updateMcpService,
  deleteMcpService as apiDeleteMcpService,
  toggleMcpService,
  refreshMcpService,
  testMcpService,
} from "@/services/mcp-service";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Plus, Pencil, Trash2, Play, RefreshCw, Power, PowerOff, CheckCircle2, XCircle, CircleDashed, Info } from "lucide-react";
import { toast } from "sonner";

interface ConfigForm {
  command: string;
  args: string;
  env: string;
  url: string;
  headers: string;
}

interface McpFormData {
  name: string;
  description: string;
  protocol: McpProtocol;
  config: ConfigForm;
}

const emptyForm: McpFormData = {
  name: "",
  description: "",
  protocol: "STDIO",
  config: { command: "", args: "", env: "", url: "", headers: "" },
};

const statusMeta: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline"; icon: React.ReactNode }> = {
  CONNECTED: { label: "已连接", variant: "default", icon: <CheckCircle2 className="mr-1 size-3" /> },
  DISCONNECTED: { label: "未连接", variant: "outline", icon: <CircleDashed className="mr-1 size-3" /> },
  ERROR: { label: "连接异常", variant: "destructive", icon: <XCircle className="mr-1 size-3" /> },
};

function buildConfigJson(protocol: McpProtocol, cfg: ConfigForm): string {
  if (protocol === "STDIO") {
    const args = cfg.args.split(",").map((s) => s.trim()).filter(Boolean);
    const env: Record<string, string> = {};
    for (const line of cfg.env.split("\n")) {
      const trimmed = line.trim();
      if (!trimmed) continue;
      const eq = trimmed.indexOf("=");
      if (eq > 0) env[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
    }
    return JSON.stringify({ command: cfg.command.trim(), args, env });
  }
  const headers: Record<string, string> = {};
  for (const line of cfg.headers.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    const eq = trimmed.indexOf(":");
    if (eq > 0) headers[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim();
  }
  return JSON.stringify({ url: cfg.url.trim(), headers });
}

function parseConfigJson(protocol: McpProtocol, json: string): ConfigForm {
  const base: ConfigForm = { command: "", args: "", env: "", url: "", headers: "" };
  try {
    const obj = JSON.parse(json) as Record<string, unknown>;
    if (protocol === "STDIO") {
      base.command = String(obj.command ?? "");
      base.args = Array.isArray(obj.args) ? (obj.args as string[]).join(",") : "";
      if (obj.env && typeof obj.env === "object") {
        base.env = Object.entries(obj.env as Record<string, string>)
          .map(([k, v]) => `${k}=${v}`)
          .join("\n");
      }
    } else {
      base.url = String(obj.url ?? "");
      if (obj.headers && typeof obj.headers === "object") {
        base.headers = Object.entries(obj.headers as Record<string, string>)
          .map(([k, v]) => `${k}:${v}`)
          .join("\n");
      }
    }
  } catch {
    // ignore
  }
  return base;
}

export function McpServicePage() {
  const [services, setServices] = useState<McpServiceDTO[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      setServices(await listMcpServices());
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<McpServiceDTO | null>(null);
  const [form, setForm] = useState<McpFormData>(emptyForm);
  const [saving, setSaving] = useState(false);

  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<McpTestResultDTO | null>(null);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setTestResult(null);
    setDialogOpen(true);
  };

  const openEdit = (svc: McpServiceDTO) => {
    setEditing(svc);
    setForm({
      name: svc.name,
      description: svc.description ?? "",
      protocol: svc.protocol,
      config: parseConfigJson(svc.protocol, svc.config),
    });
    setTestResult(null);
    setDialogOpen(true);
  };

  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const result = await testMcpService({
        protocol: form.protocol,
        config: buildConfigJson(form.protocol, form.config),
      });
      setTestResult(result);
    } catch (err) {
      setTestResult({ success: false, errorMessage: err instanceof Error ? err.message : "请求失败", tools: [] });
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async () => {
    if (!form.name || (form.protocol === "STDIO" && !form.config.command)) {
      toast.error("请填写必填字段");
      return;
    }
    if (form.protocol !== "STDIO" && !form.config.url) {
      toast.error("请填写必填字段");
      return;
    }
    setSaving(true);
    try {
      const payload = {
        name: form.name,
        description: form.description || undefined,
        protocol: form.protocol,
        config: buildConfigJson(form.protocol, form.config),
      };
      if (editing) {
        await updateMcpService(editing.id, payload);
      } else {
        await createMcpService(payload);
      }
      await fetchData();
      setDialogOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<McpServiceDTO | null>(null);

  const openDelete = (svc: McpServiceDTO) => {
    setDeleting(svc);
    setDeleteOpen(true);
  };

  const [detailOpen, setDetailOpen] = useState(false);
  const [detailService, setDetailService] = useState<McpServiceDTO | null>(null);

  const openDetails = (svc: McpServiceDTO) => {
    setDetailService(svc);
    setDetailOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteMcpService(deleting.id);
      await fetchData();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const handleToggle = async (svc: McpServiceDTO) => {
    try {
      await toggleMcpService(svc.id);
      await fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "操作失败");
    }
  };

  const handleRefresh = async (svc: McpServiceDTO) => {
    try {
      await refreshMcpService(svc.id);
      await fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "刷新失败");
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">MCP 服务</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            添加服务
          </Button>
          <Button variant="outline" size="icon" onClick={fetchData} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : services.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">暂无 MCP 服务，点击「添加服务」创建</div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>协议</TableHead>
              <TableHead>状态</TableHead>
              <TableHead>工具数</TableHead>
              <TableHead>启用</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {services.map((svc) => {
              const st = statusMeta[svc.status] ?? statusMeta.DISCONNECTED;
              return (
                <TableRow key={svc.id}>
                  <TableCell>
                    <div className="flex flex-col">
                      <span className="font-medium">{svc.name}</span>
                      {svc.description && (
                        <span className="text-xs text-muted-foreground">{svc.description}</span>
                      )}
                      {svc.status === "ERROR" && svc.errorMessage && (
                        <span className="text-xs text-destructive">{svc.errorMessage}</span>
                      )}
                    </div>
                  </TableCell>
                  <TableCell><code className="text-xs">{svc.protocol}</code></TableCell>
                  <TableCell>
                    <Badge variant={st.variant} className="whitespace-nowrap">
                      {st.icon}
                      {st.label}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {svc.tools.length > 0 ? (
                      <span title={svc.tools.map((t) => t.name).join(", ")}>
                        {svc.tools.length}
                      </span>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </TableCell>
                  <TableCell>
                    {svc.enabled ? (
                      <Badge variant="outline">已启用</Badge>
                    ) : (
                      <Badge variant="secondary">未启用</Badge>
                    )}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon-sm" onClick={() => openDetails(svc)} title="工具详情">
                        <Info className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => handleToggle(svc)}
                        title={svc.enabled ? "禁用" : "启用"}
                      >
                        {svc.enabled ? <PowerOff className="size-4" /> : <Power className="size-4" />}
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => handleRefresh(svc)} title="刷新状态">
                        <RefreshCw className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => openEdit(svc)} title="编辑">
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => openDelete(svc)} title="删除">
                        <Trash2 className="size-4 text-destructive" />
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑 MCP 服务" : "添加 MCP 服务"}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="flex flex-col gap-2">
                <Label>服务名</Label>
                <Input
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  placeholder="唯一标识，作为工具名前缀"
                />
              </div>
              <div className="flex flex-col gap-2">
                <Label>协议</Label>
                <Select
                  value={form.protocol}
                  onValueChange={(v) => setForm({ ...form, protocol: v as McpProtocol, config: emptyForm.config })}
                >
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="STDIO">STDIO（本地子进程）</SelectItem>
                    <SelectItem value="STREAMABLE_HTTP">Streamable HTTP</SelectItem>
                    <SelectItem value="SSE">SSE（legacy）</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Label>描述</Label>
              <Input
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </div>

            {form.protocol === "STDIO" ? (
              <>
                <div className="flex flex-col gap-2">
                  <Label>命令</Label>
                  <Input
                    value={form.config.command}
                    onChange={(e) => setForm({ ...form, config: { ...form.config, command: e.target.value } })}
                    placeholder="如 npx"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <Label>参数（逗号分隔）</Label>
                  <Input
                    value={form.config.args}
                    onChange={(e) => setForm({ ...form, config: { ...form.config, args: e.target.value } })}
                    placeholder="如 -y,@modelcontextprotocol/server-everything"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <Label>环境变量（每行 key=value）</Label>
                  <Textarea
                    rows={3}
                    value={form.config.env}
                    onChange={(e) => setForm({ ...form, config: { ...form.config, env: e.target.value } })}
                    placeholder={"API_KEY=xxx"}
                  />
                </div>
              </>
            ) : (
              <>
                <div className="flex flex-col gap-2">
                  <Label>URL</Label>
                  <Input
                    value={form.config.url}
                    onChange={(e) => setForm({ ...form, config: { ...form.config, url: e.target.value } })}
                    placeholder="https://example.com/mcp"
                  />
                </div>
                <div className="flex flex-col gap-2">
                  <Label>请求头（每行 key:value）</Label>
                  <Textarea
                    rows={3}
                    value={form.config.headers}
                    onChange={(e) => setForm({ ...form, config: { ...form.config, headers: e.target.value } })}
                    placeholder={"Authorization:Bearer xxx"}
                  />
                </div>
              </>
            )}

            <div className="flex items-center gap-2">
              <Button variant="outline" type="button" onClick={handleTest} disabled={testing}>
                {testing ? "测试中..." : "测试连接"}
                <Play className="ml-1 size-4" />
              </Button>
              {testResult && (
                <span className={testResult.success ? "text-sm text-primary" : "text-sm text-destructive"}>
                  {testResult.success ? "连接成功" : `连接失败: ${testResult.errorMessage}`}
                </span>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>取消</Button>
            <Button onClick={handleSave} disabled={saving || !form.name}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除 MCP 服务「{deleting?.name}」吗？若该服务正被智能体使用，删除将被拒绝。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>工具详情 — {detailService?.name}</DialogTitle>
          </DialogHeader>
          {!detailService || detailService.tools.length === 0 ? (
            <div className="py-4 text-center text-muted-foreground">
              {detailService?.status === "CONNECTED" ? "该服务暂无工具" : "服务未连接，暂无工具信息"}
            </div>
          ) : (
            <div className="flex flex-col gap-3">
              {detailService.tools.map((t) => (
                <div key={t.name} className="rounded-md border p-3">
                  <code className="text-sm font-medium">{t.name}</code>
                  {t.description && (
                    <p className="mt-1 text-sm text-muted-foreground">{t.description}</p>
                  )}
                </div>
              ))}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
