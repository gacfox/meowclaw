import { useEffect, useRef, useState, type ChangeEvent } from "react";
import { toast } from "sonner";
import { Download, Loader2, Upload } from "lucide-react";
import {
  getTraceObservationEnabled,
  getTraceRetentionPolicy,
  updateTraceObservationEnabled,
  updateTraceRetentionPolicy,
} from "@/services/trace";
import {
  exportAgentConfig,
  importAgentConfig,
  type ConfigImportResult,
} from "@/services/systemConfig";
import {
  getSystemResourceMonitorEnabled,
  SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT,
  updateSystemResourceMonitorEnabled,
} from "@/services/systemResource";
import type { TraceRetentionPolicy } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
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

const RETENTION_OPTIONS: { value: TraceRetentionPolicy; label: string }[] = [
  { value: "DAYS_1", label: "最近 1 天" },
  { value: "DAYS_3", label: "最近 3 天" },
  { value: "DAYS_7", label: "最近 7 天" },
  { value: "MONTH_1", label: "最近 1 个月" },
  { value: "YEAR_1", label: "最近 1 年" },
  { value: "ALL", label: "全部留存" },
];

const CONFIG_TYPE_LABELS: Record<string, string> = {
  LLM: "大语言模型",
  EMBEDDING_MODEL: "向量嵌入模型",
  MCP_SERVICE: "MCP 服务",
  GUARDRAIL_POLICY: "护栏策略",
  AGENT: "智能体",
  ENVIRONMENT_VARIABLE: "环境变量",
  SCHEDULED_TASK: "定时任务",
};

const IMPORT_STATUS_LABELS = {
  IMPORTED: "已导入",
  SKIPPED: "已跳过",
  FAILED: "失败",
} as const;

const IMPORT_STATUS_VARIANTS = {
  IMPORTED: "default",
  SKIPPED: "secondary",
  FAILED: "destructive",
} as const;

function createExportFileName() {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  const date = `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}`;
  const time = `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
  return `meowclaw-agent-config-${date}-${time}.json`;
}

export function SystemConfigPage() {
  const [traceEnabled, setTraceEnabled] = useState(true);
  const [resourceMonitorEnabled, setResourceMonitorEnabled] = useState(true);
  const [retentionPolicy, setRetentionPolicy] = useState<TraceRetentionPolicy>("ALL");
  const [pendingRetentionPolicy, setPendingRetentionPolicy] = useState<TraceRetentionPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [enabledSaving, setEnabledSaving] = useState(false);
  const [resourceMonitorSaving, setResourceMonitorSaving] = useState(false);
  const [retentionSaving, setRetentionSaving] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [importing, setImporting] = useState(false);
  const [pendingImportFile, setPendingImportFile] = useState<File | null>(null);
  const [importResult, setImportResult] = useState<ConfigImportResult | null>(null);
  const importFileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const loadConfig = async () => {
      try {
        const [enabled, policy, resourceEnabled] = await Promise.all([
          getTraceObservationEnabled(),
          getTraceRetentionPolicy(),
          getSystemResourceMonitorEnabled(),
        ]);
        setTraceEnabled(enabled);
        setRetentionPolicy(policy);
        setResourceMonitorEnabled(resourceEnabled);
      } catch (err) {
        toast.error(err instanceof Error ? err.message : "加载系统配置失败");
      } finally {
        setLoading(false);
      }
    };
    void loadConfig();
  }, []);

  const handleEnabledChange = async (enabled: boolean) => {
    if (enabledSaving) return;
    setEnabledSaving(true);
    try {
      await updateTraceObservationEnabled(enabled);
      setTraceEnabled(enabled);
      toast.success(enabled ? "已开启追踪观测" : "已关闭追踪观测");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存追踪观测开关失败");
    } finally {
      setEnabledSaving(false);
    }
  };

  const handleResourceMonitorEnabledChange = async (enabled: boolean) => {
    if (resourceMonitorSaving) return;
    setResourceMonitorSaving(true);
    try {
      await updateSystemResourceMonitorEnabled(enabled);
      setResourceMonitorEnabled(enabled);
      window.dispatchEvent(new CustomEvent<boolean>(SYSTEM_RESOURCE_MONITOR_CHANGED_EVENT, {
        detail: enabled,
      }));
      toast.success(enabled ? "已开启系统资源监控" : "已关闭系统资源监控");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存系统资源监控开关失败");
    } finally {
      setResourceMonitorSaving(false);
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

  const handleExport = async () => {
    if (exporting) return;
    setExporting(true);
    try {
      const blob = await exportAgentConfig();
      const objectUrl = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = objectUrl;
      link.download = createExportFileName();
      link.style.display = "none";
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      toast.success("配置导出已开始");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "导出智能体配置失败");
    } finally {
      setExporting(false);
    }
  };

  const handleImportFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file || importing) return;
    setPendingImportFile(file);
  };

  const handleImport = async (file: File) => {
    if (importing) return;
    setImporting(true);
    try {
      const result = await importAgentConfig(file);
      setPendingImportFile(null);
      setImportResult(result);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "导入智能体配置失败");
    } finally {
      setImporting(false);
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold">系统配置</h1>
        <p className="text-sm text-muted-foreground">管理系统级功能与数据处理策略。</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>追踪观测</CardTitle>
          <CardDescription>管理 LLM 调用追踪的记录与自动清理。</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="flex items-start justify-between gap-6 border-b pb-6">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="trace-enabled" className="text-sm font-medium">启用追踪观测</Label>
              <p className="max-w-xl text-sm text-muted-foreground">
                关闭后不再记录新的 LLM 调用追踪，已有记录仍可查看和清理，不影响会话及 Tokens 统计。
              </p>
            </div>
            <Switch
              id="trace-enabled"
              checked={traceEnabled}
              onCheckedChange={(enabled) => void handleEnabledChange(enabled)}
              disabled={loading || enabledSaving}
              aria-label="启用追踪观测"
            />
          </div>

          <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="trace-retention-policy" className="text-sm font-medium">数据留存策略</Label>
              <p className="max-w-xl text-sm text-muted-foreground">
                超出保留期限的追踪明细将由后台任务自动清理，不影响会话及 Tokens 统计。
              </p>
            </div>
            <Select
              value={retentionPolicy}
              onValueChange={(value) => handleRetentionPolicyChange(value as TraceRetentionPolicy)}
              disabled={loading || retentionSaving}
            >
              <SelectTrigger id="trace-retention-policy" className="w-full shrink-0 sm:w-48">
                <SelectValue placeholder="选择留存策略" />
              </SelectTrigger>
              <SelectContent>
                {RETENTION_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>系统资源监控</CardTitle>
          <CardDescription>控制页面顶部是否展示服务端 CPU 和内存占用。</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex items-start justify-between gap-6">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="system-resource-monitor-enabled" className="text-sm font-medium">
                启用系统资源监控
              </Label>
              <p className="max-w-xl text-sm text-muted-foreground">
                开启后，可点击顶部图标查看 CPU 和内存占用；弹出层打开时每 10 秒刷新，关闭后停止刷新。
              </p>
            </div>
            <Switch
              id="system-resource-monitor-enabled"
              checked={resourceMonitorEnabled}
              onCheckedChange={(enabled) => void handleResourceMonitorEnabledChange(enabled)}
              disabled={loading || resourceMonitorSaving}
              aria-label="启用系统资源监控"
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>智能体配置</CardTitle>
          <CardDescription>
            导出智能体、MCP、定时任务、大语言模型、向量嵌入模型、护栏策略和环境变量；不包含会话、工作区及运行数据。
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="max-w-xl text-sm text-muted-foreground">
            导出文件包含明文密钥等敏感信息，请妥善保管，避免泄露。
          </p>
          <div className="flex shrink-0 flex-wrap gap-2">
            <Button type="button" variant="outline" onClick={() => void handleExport()} disabled={exporting}>
              {exporting ? <Loader2 className="animate-spin" /> : <Download />}
              {exporting ? "导出中..." : "导出"}
            </Button>
            <Button type="button" onClick={() => importFileRef.current?.click()} disabled={importing}>
              {importing ? <Loader2 className="animate-spin" /> : <Upload />}
              {importing ? "导入中..." : "导入"}
            </Button>
            <input
              ref={importFileRef}
              type="file"
              accept=".json,application/json"
              className="hidden"
              onChange={handleImportFileChange}
              disabled={importing}
            />
          </div>
        </CardContent>
      </Card>

      <AlertDialog
        open={pendingRetentionPolicy !== null}
        onOpenChange={(open) => {
          if (!open && !retentionSaving) setPendingRetentionPolicy(null);
        }}
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

      <AlertDialog
        open={pendingImportFile !== null}
        onOpenChange={(open) => {
          if (!open && !importing) setPendingImportFile(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认导入智能体配置</AlertDialogTitle>
            <AlertDialogDescription>
              即将导入“{pendingImportFile?.name}”。导入不会覆盖已有配置；MCP 和护栏策略重名时自动改名，同作用域环境变量冲突时跳过；单项失败后仍会继续处理其他配置；MCP 和定时任务导入后保持禁用。确定继续吗？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={importing}>取消</AlertDialogCancel>
            <AlertDialogAction
              disabled={importing}
              onClick={(event) => {
                event.preventDefault();
                if (pendingImportFile) void handleImport(pendingImportFile);
              }}
            >
              {importing ? "导入中..." : "确认导入"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog
        open={importResult !== null}
        onOpenChange={(open) => {
          if (!open) setImportResult(null);
        }}
      >
        <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden sm:max-w-3xl">
          <DialogHeader>
            <DialogTitle>智能体配置导入结果</DialogTitle>
            <DialogDescription>
              仅展示配置名称、状态与结果消息，不展示密钥等敏感配置值。
            </DialogDescription>
          </DialogHeader>
          {importResult && (
            <div className="min-h-0 flex-1 space-y-4 overflow-y-auto pr-1">
              <div className="grid grid-cols-2 gap-2 sm:grid-cols-5">
                {[
                  { label: "总计", value: importResult.totalCount },
                  { label: "已导入", value: importResult.importedCount },
                  { label: "已跳过", value: importResult.skippedCount },
                  { label: "失败", value: importResult.failedCount },
                  { label: "自动改名", value: importResult.renamedCount },
                ].map((summary) => (
                  <div key={summary.label} className="rounded-lg border p-3">
                    <div className="text-xs text-muted-foreground">{summary.label}</div>
                    <div className="mt-1 text-lg font-semibold">{summary.value}</div>
                  </div>
                ))}
              </div>

              <section>
                <h3 className="text-sm font-medium">分类统计</h3>
                {importResult.categories.length === 0 ? (
                  <p className="mt-2 text-sm text-muted-foreground">暂无分类统计</p>
                ) : (
                  <div className="mt-2 grid gap-2 sm:grid-cols-2">
                    {importResult.categories.map((category) => (
                      <div key={category.type} className="rounded-lg border p-3">
                        <div className="flex items-center justify-between gap-2">
                          <span className="text-sm font-medium">
                            {CONFIG_TYPE_LABELS[category.type] ?? category.type}
                          </span>
                          <span className="text-xs text-muted-foreground">共 {category.totalCount} 项</span>
                        </div>
                        <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
                          <span>导入 {category.importedCount}</span>
                          <span>跳过 {category.skippedCount}</span>
                          <span>失败 {category.failedCount}</span>
                          <span>改名 {category.renamedCount}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </section>

              <section>
                <h3 className="text-sm font-medium">逐项消息</h3>
                {importResult.items.length === 0 ? (
                  <p className="mt-2 text-sm text-muted-foreground">暂无逐项消息</p>
                ) : (
                  <div className="mt-2 max-h-72 space-y-2 overflow-y-auto pr-1">
                    {importResult.items.map((item, index) => (
                      <div key={`${item.type}-${item.sourceId ?? "none"}-${index}`} className="rounded-lg border p-3">
                        <div className="flex flex-wrap items-center gap-2">
                          <Badge variant={IMPORT_STATUS_VARIANTS[item.status]}>
                            {IMPORT_STATUS_LABELS[item.status]}
                          </Badge>
                          <span className="text-xs text-muted-foreground">
                            {CONFIG_TYPE_LABELS[item.type] ?? item.type}
                          </span>
                          <span className="min-w-0 break-all text-sm font-medium">
                            {item.finalName ?? item.originalName ?? "未命名配置"}
                          </span>
                        </div>
                        {item.originalName && item.finalName && item.originalName !== item.finalName && (
                          <p className="mt-2 break-all text-xs text-muted-foreground">
                            名称：{item.originalName} → {item.finalName}
                          </p>
                        )}
                        {(item.sourceId !== null || item.newId !== null) && (
                          <div className="mt-1 flex flex-wrap gap-x-3 text-xs text-muted-foreground">
                            {item.sourceId !== null && <span>源 ID：{item.sourceId}</span>}
                            {item.newId !== null && <span>新 ID：{item.newId}</span>}
                          </div>
                        )}
                        <p className="mt-2 whitespace-pre-wrap break-words text-xs text-muted-foreground">
                          {item.message ?? "无附加消息"}
                        </p>
                      </div>
                    ))}
                  </div>
                )}
              </section>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
