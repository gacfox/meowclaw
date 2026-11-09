import { useCallback, useEffect, useRef, useState } from "react";
import type { AgentDTO, EnvironmentVariableDTO } from "@/types";
import { listAgents } from "@/services/agent";
import {
  createEnvironmentVariable,
  deleteEnvironmentVariable,
  listEnvironmentVariables,
  updateEnvironmentVariable,
} from "@/services/environmentVariable";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
import { Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

const ENVIRONMENT_VARIABLE_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;
const GLOBAL_SCOPE = "GLOBAL";

export function EnvironmentVariablePage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [agentsLoading, setAgentsLoading] = useState(true);
  const [agentsError, setAgentsError] = useState<string | null>(null);
  const [selectedScope, setSelectedScope] = useState(GLOBAL_SCOPE);
  const [rows, setRows] = useState<EnvironmentVariableDTO[]>([]);
  const [variablesLoading, setVariablesLoading] = useState(false);
  const [variablesError, setVariablesError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingVariable, setEditingVariable] = useState<EnvironmentVariableDTO | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<EnvironmentVariableDTO | null>(null);
  const variableRequestId = useRef(0);

  const currentAgentId = selectedScope === GLOBAL_SCOPE ? null : Number(selectedScope);
  const currentAgent = agents.find((agent) => agent.id === currentAgentId);

  const loadAgents = useCallback(async () => {
    setAgentsLoading(true);
    setAgentsError(null);
    try {
      const agentList = await listAgents();
      setAgents(agentList);
      setSelectedScope((current) => (
        current === GLOBAL_SCOPE || agentList.some((agent) => agent.id.toString() === current)
          ? current
          : GLOBAL_SCOPE
      ));
    } catch (error) {
      const message = error instanceof Error ? error.message : "加载智能体失败";
      setAgentsError(message);
      toast.error(message);
    } finally {
      setAgentsLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadAgents();
  }, [loadAgents]);

  const loadVariables = useCallback(async (scope: string) => {
    const requestId = ++variableRequestId.current;
    setVariablesLoading(true);
    setVariablesError(null);
    try {
      const data = await listEnvironmentVariables(
        scope === GLOBAL_SCOPE ? undefined : Number(scope),
      );
      if (requestId !== variableRequestId.current) return;
      setRows(data);
    } catch (error) {
      if (requestId !== variableRequestId.current) return;
      const message = error instanceof Error ? error.message : "加载环境变量失败";
      setVariablesError(message);
      toast.error(message);
    } finally {
      if (requestId === variableRequestId.current) {
        setVariablesLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    setRows([]);
    setVariablesError(null);
    setEditorOpen(false);
    setDeleteTarget(null);
    void loadVariables(selectedScope);
  }, [selectedScope, loadVariables]);

  const openAddDialog = () => {
    setEditingVariable(null);
    setKeyInput("");
    setValueInput("");
    setEditorOpen(true);
  };

  const openEditDialog = (row: EnvironmentVariableDTO) => {
    setEditingVariable(row);
    setKeyInput(row.key);
    setValueInput(row.value);
    setEditorOpen(true);
  };

  const handleEditorSave = async () => {
    const key = keyInput.trim();
    if (!ENVIRONMENT_VARIABLE_KEY_PATTERN.test(key)) {
      toast.error("Key 无效，需匹配 [A-Za-z_][A-Za-z0-9_]*");
      return;
    }
    const scopeAgentId = editingVariable ? editingVariable.agentId : currentAgentId;
    const duplicate = rows.some((row) =>
      row.id !== editingVariable?.id
      && row.agentId === scopeAgentId
      && row.key.toLowerCase() === key.toLowerCase()
    );
    if (duplicate) {
      toast.error("Key 已存在，请勿重复添加");
      return;
    }

    const editing = editingVariable;
    setSaving(true);
    try {
      if (editing) {
        const savedVariable = await updateEnvironmentVariable(editing.id, {
          key,
          value: valueInput,
        });
        setRows((currentRows) => currentRows.map((row) => (
          row.id === savedVariable.id ? savedVariable : row
        )));
      } else {
        const savedVariable = await createEnvironmentVariable({
          agentId: currentAgentId,
          key,
          value: valueInput,
        });
        setRows((currentRows) => [...currentRows, savedVariable]);
      }
      setEditorOpen(false);
      toast.success(editing ? "变量已更新" : "变量已添加");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存环境变量失败");
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    const target = deleteTarget;
    setSaving(true);
    try {
      await deleteEnvironmentVariable(target.id);
      setRows((currentRows) => currentRows.filter((row) => row.id !== target.id));
      setDeleteTarget(null);
      toast.success("变量已删除");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除环境变量失败");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">环境变量</h1>
        <div className="flex items-center gap-2">
          {agentsError && (
            <Button variant="ghost" size="sm" onClick={() => void loadAgents()}>
              重试加载智能体
            </Button>
          )}
          <Select
            value={selectedScope}
            onValueChange={setSelectedScope}
            disabled={agentsLoading || saving}
          >
            <SelectTrigger className="w-[220px]">
              <SelectValue placeholder="选择作用域" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={GLOBAL_SCOPE}>全局</SelectItem>
              {agents.map((agent) => (
                <SelectItem key={agent.id} value={agent.id.toString()}>
                  {agent.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button
            variant="outline"
            size="icon"
            onClick={() => void loadVariables(selectedScope)}
            title="刷新"
            aria-label="刷新"
            disabled={variablesLoading || saving}
          >
            <RefreshCw className={variablesLoading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      <div className="flex justify-end">
        <Button
          onClick={openAddDialog}
          disabled={variablesLoading || saving || variablesError !== null}
        >
          <Plus className="mr-1 size-4" />
          添加变量
        </Button>
      </div>

      {variablesLoading ? (
        <div className="py-8 text-center text-muted-foreground">加载环境变量中...</div>
      ) : variablesError ? (
        <div className="flex flex-col items-center gap-3 py-8 text-sm">
          <span className="text-destructive">加载环境变量失败：{variablesError}</span>
          <Button variant="outline" onClick={() => void loadVariables(selectedScope)}>
            重试
          </Button>
        </div>
      ) : rows.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">
          暂无环境变量，点击「添加变量」创建
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="w-[30%]">Key</TableHead>
              <TableHead>Value</TableHead>
              <TableHead className="w-36">来源</TableHead>
              <TableHead className="w-24 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.map((row) => {
              const readOnly = currentAgentId !== null && row.agentId === null;
              return (
                <TableRow key={row.id}>
                  <TableCell className="break-all font-mono">{row.key}</TableCell>
                  <TableCell className="whitespace-pre-wrap break-all">{row.value}</TableCell>
                  <TableCell>
                    {row.agentId === null ? "全局" : currentAgent?.name ?? `Agent ${row.agentId}`}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => openEditDialog(row)}
                        title="编辑变量"
                        aria-label={`编辑变量 ${row.key}`}
                        disabled={saving || readOnly}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        onClick={() => setDeleteTarget(row)}
                        title="删除变量"
                        aria-label={`删除变量 ${row.key}`}
                        disabled={saving || readOnly}
                      >
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

      <Dialog open={editorOpen} onOpenChange={(open) => !saving && setEditorOpen(open)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingVariable == null ? "添加变量" : "编辑变量"}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="environment-key">Key</Label>
            <Input
              id="environment-key"
              value={keyInput}
              onChange={(event) => setKeyInput(event.target.value)}
              placeholder="例如 API_KEY"
              className="font-mono"
              disabled={saving}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="environment-value">Value</Label>
            <Input
              id="environment-value"
              value={valueInput}
              onChange={(event) => setValueInput(event.target.value)}
              placeholder="环境变量值，可为空"
              disabled={saving}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditorOpen(false)} disabled={saving}>
              取消
            </Button>
            <Button onClick={() => void handleEditorSave()} disabled={saving}>
              {saving ? "保存中..." : "保存"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={deleteTarget != null}
        onOpenChange={(open) => !open && !saving && setDeleteTarget(null)}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除环境变量</AlertDialogTitle>
            <AlertDialogDescription>
              确定删除环境变量 {deleteTarget?.key} 吗？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={saving}>取消</AlertDialogCancel>
            <AlertDialogAction
              onClick={(event) => {
                event.preventDefault();
                void handleDelete();
              }}
              disabled={saving}
            >
              {saving ? "删除中..." : "删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
