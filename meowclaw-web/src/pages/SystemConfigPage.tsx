import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  getTraceObservationEnabled,
  getTraceRetentionPolicy,
  updateTraceObservationEnabled,
  updateTraceRetentionPolicy,
} from "@/services/trace";
import type { TraceRetentionPolicy } from "@/types";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
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

export function SystemConfigPage() {
  const [traceEnabled, setTraceEnabled] = useState(true);
  const [retentionPolicy, setRetentionPolicy] = useState<TraceRetentionPolicy>("ALL");
  const [pendingRetentionPolicy, setPendingRetentionPolicy] = useState<TraceRetentionPolicy | null>(null);
  const [loading, setLoading] = useState(true);
  const [enabledSaving, setEnabledSaving] = useState(false);
  const [retentionSaving, setRetentionSaving] = useState(false);

  useEffect(() => {
    const loadConfig = async () => {
      try {
        const [enabled, policy] = await Promise.all([
          getTraceObservationEnabled(),
          getTraceRetentionPolicy(),
        ]);
        setTraceEnabled(enabled);
        setRetentionPolicy(policy);
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
    </div>
  );
}
