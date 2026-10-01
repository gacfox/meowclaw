import { useState, useEffect, useCallback } from "react";
import type { GuardrailPolicyDTO } from "@/types";
import { listGuardrailPolicies, createGuardrailPolicy, updateGuardrailPolicy, deleteGuardrailPolicy } from "@/services/guardrail";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { JsonCodeEditor } from "@/components/json-viewer/JsonCodeEditor";
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
import { Plus, Trash2, Copy, Shield, ShieldCheck, RefreshCw } from "lucide-react";
import { toast } from "sonner";

const NEW_POLICY_TEMPLATE = `{
  "defaultDecision": "ask",
  "rules": [
    {
      "tools": ["read", "write"],
      "paths": ["\${cwd}/**"],
      "action": "allow"
    }
  ]
}`;

function validateConfigLocal(configJson: string): string | null {
  let obj: unknown;
  try {
    obj = JSON.parse(configJson);
  } catch (e) {
    return `JSON 语法错误: ${e instanceof Error ? e.message : "无法解析"}`;
  }
  if (obj === null || typeof obj !== "object" || Array.isArray(obj)) {
    return "配置必须是JSON对象";
  }
  const config = obj as Record<string, unknown>;
  if (!["allow", "ask", "deny"].includes(config.defaultDecision as string)) {
    return "defaultDecision 必填且必须为 allow/ask/deny 之一";
  }
  if (config.rules !== undefined) {
    if (!Array.isArray(config.rules)) return "rules 必须是数组";
    for (let i = 0; i < config.rules.length; i++) {
      const rule = config.rules[i] as Record<string, unknown>;
      if (!Array.isArray(rule.tools) || rule.tools.length === 0) return `rules[${i}]: tools 必填且为非空数组`;
      if (!["allow", "ask", "deny"].includes(rule.action as string)) return `rules[${i}]: action 必填且必须为 allow/ask/deny 之一`;
    }
  }
  return null;
}

export function GuardrailPolicyPage() {
  const [policies, setPolicies] = useState<GuardrailPolicyDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<number | "new" | null>(null);
  const [name, setName] = useState("");
  const [configJson, setConfigJson] = useState("");
  const [configError, setConfigError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<GuardrailPolicyDTO | null>(null);

  const selected = typeof selectedId === "number" ? policies.find((p) => p.id === selectedId) : undefined;
  const isNew = selectedId === "new";
  const readOnly = selected?.builtin === true;

  const fetchPolicies = useCallback(async () => {
    setLoading(true);
    try {
      setPolicies(await listGuardrailPolicies());
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "加载策略失败");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    listGuardrailPolicies()
      .then((list) => { if (!cancelled) setPolicies(list); })
      .catch((e) => toast.error(e instanceof Error ? e.message : "加载策略失败"))
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, []);

  const selectPolicy = (p: GuardrailPolicyDTO) => {
    setSelectedId(p.id);
    setName(p.name);
    setConfigJson(p.configJson);
    setConfigError(null);
  };

  const startNew = () => {
    setSelectedId("new");
    setName("");
    setConfigJson(NEW_POLICY_TEMPLATE);
    setConfigError(null);
  };

  const copyAsCustom = () => {
    if (!selected) return;
    setSelectedId("new");
    setName(`${selected.name}-副本`);
    setConfigJson(selected.configJson);
    setConfigError(null);
  };

  const handleSave = async () => {
    if (!name.trim()) {
      toast.error("策略名不能为空");
      return;
    }
    const error = validateConfigLocal(configJson);
    setConfigError(error);
    if (error) return;
    setSaving(true);
    try {
      const saved = isNew
        ? await createGuardrailPolicy(name.trim(), configJson)
        : await updateGuardrailPolicy(selectedId as number, name.trim(), configJson);
      toast.success("已保存");
      await fetchPolicies();
      selectPolicy(saved);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteGuardrailPolicy(deleteTarget.id);
      toast.success("已删除");
      if (selectedId === deleteTarget.id) setSelectedId(null);
      await fetchPolicies();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "删除失败");
    } finally {
      setDeleteTarget(null);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">安全护栏策略</h1>
        <div className="flex items-center gap-2">
          <Button onClick={startNew}>
            <Plus className="mr-1 size-4" />
            新建策略
          </Button>
          <Button variant="outline" size="icon" onClick={fetchPolicies} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      <div className="flex gap-4">
        <Card className="w-64 shrink-0 self-start">
          <CardContent className="space-y-1 p-3">
            {loading ? (
              <div className="py-4 text-center text-sm text-muted-foreground">加载中...</div>
            ) : (
              policies.map((p) => (
                <div
                  key={p.id}
                  className={`flex cursor-pointer items-center gap-2 rounded-md px-2 py-1.5 text-sm hover:bg-muted ${selectedId === p.id ? "bg-muted" : ""}`}
                  onClick={() => selectPolicy(p)}
                >
                  {p.builtin ? <ShieldCheck className="size-3.5 shrink-0 text-primary" /> : <Shield className="size-3.5 shrink-0 text-muted-foreground" />}
                  <span className="flex-1 truncate">{p.name}</span>
                  {p.builtin && <Badge variant="secondary" className="text-[10px]">内置</Badge>}
                </div>
              ))
            )}
          </CardContent>
        </Card>

        <Card className="flex-1">
          {selectedId == null ? (
            <div className="flex h-full items-center justify-center py-16 text-sm text-muted-foreground">
              选择左侧策略查看，或点击「新建策略」创建自定义策略
            </div>
          ) : (
            <CardContent className="flex flex-col gap-3 p-4">
              <div className="flex items-center justify-between">
                <div className="flex flex-1 items-center gap-2">
                  <Label className="shrink-0">策略名</Label>
                  <Input value={name} onChange={(e) => setName(e.target.value)} disabled={readOnly} className="max-w-64" />
                </div>
                <div className="flex gap-2">
                  {readOnly && (
                    <Button variant="outline" onClick={copyAsCustom}>
                      <Copy className="mr-1 size-4" />
                      复制为自定义
                    </Button>
                  )}
                  {!readOnly && !isNew && (
                    <Button variant="destructive" onClick={() => setDeleteTarget(selected!)}>
                      <Trash2 className="mr-1 size-4" />
                      删除
                    </Button>
                  )}
                  {!readOnly && (
                    <Button onClick={handleSave} disabled={saving}>
                      {saving ? "保存中..." : "保存"}
                    </Button>
                  )}
                </div>
              </div>
              <Label>策略配置（JSON）</Label>
              <JsonCodeEditor
                value={configJson}
                onChange={setConfigJson}
                readOnly={readOnly}
              />
              {configError && <div className="text-xs text-destructive">{configError}</div>}
            </CardContent>
          )}
        </Card>
      </div>

      <AlertDialog open={deleteTarget != null} onOpenChange={(open) => { if (!open) setDeleteTarget(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除策略</AlertDialogTitle>
            <AlertDialogDescription>确定要删除策略「{deleteTarget?.name}」吗？引用该策略的会话将回退到默认策略。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
