import { useCallback, useEffect, useRef, useState } from "react";
import type { AgentDTO } from "@/types";
import {
  getAgentEnvironmentVariables,
  listAgents,
  updateAgentEnvironmentVariables,
} from "@/services/agent";
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
import { readSelectedAgentId, writeSelectedAgentId } from "@/lib/selectedAgent";

const ENVIRONMENT_VARIABLE_KEY_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

interface EnvironmentVariableRow {
  id: number;
  key: string;
  value: string;
}

export function EnvironmentVariablePage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [agentsLoading, setAgentsLoading] = useState(true);
  const [agentsError, setAgentsError] = useState<string | null>(null);
  const [selectedAgentId, setSelectedAgentId] = useState("");
  const [rows, setRows] = useState<EnvironmentVariableRow[]>([]);
  const [variablesLoading, setVariablesLoading] = useState(false);
  const [variablesError, setVariablesError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [editorOpen, setEditorOpen] = useState(false);
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const [keyInput, setKeyInput] = useState("");
  const [valueInput, setValueInput] = useState("");
  const [deleteTarget, setDeleteTarget] = useState<EnvironmentVariableRow | null>(null);
  const nextRowId = useRef(1);
  const variableRequestId = useRef(0);

  const loadAgents = useCallback(async () => {
    setAgentsLoading(true);
    setAgentsError(null);
    try {
      const agentList = await listAgents();
      setAgents(agentList);
      const cachedAgentId = readSelectedAgentId();
      const validAgentId = cachedAgentId != null && agentList.some((agent) => agent.id === cachedAgentId)
        ? cachedAgentId
        : agentList[0]?.id ?? null;
      setSelectedAgentId((current) => {
        if (current && agentList.some((agent) => agent.id.toString() === current)) {
          return current;
        }
        return validAgentId?.toString() ?? "";
      });
      if (validAgentId != null) writeSelectedAgentId(validAgentId);
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

  const loadVariables = useCallback(async (agentId: string) => {
    const requestId = ++variableRequestId.current;
    setVariablesLoading(true);
    setVariablesError(null);
    try {
      const data = await getAgentEnvironmentVariables(Number(agentId));
      if (requestId !== variableRequestId.current) return;
      const nextRows = Object.entries(data.variables).map(([key, value], index) => ({
        id: index + 1,
        key,
        value,
      }));
      nextRowId.current = nextRows.length + 1;
      setRows(nextRows);
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
    if (!selectedAgentId) {
      variableRequestId.current += 1;
      setVariablesLoading(false);
      return;
    }
    void loadVariables(selectedAgentId);
  }, [selectedAgentId, loadVariables]);

  const persistRows = async (nextRows: EnvironmentVariableRow[]) => {
    if (!selectedAgentId) return false;
    const variables: Record<string, string> = {};
    for (const row of nextRows) {
      variables[row.key] = row.value;
    }
    setSaving(true);
    try {
      const data = await updateAgentEnvironmentVariables(Number(selectedAgentId), variables);
      const savedRows = Object.entries(data.variables).map(([key, value], index) => ({
        id: index + 1,
        key,
        value,
      }));
      nextRowId.current = savedRows.length + 1;
      setRows(savedRows);
      return true;
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "保存环境变量失败");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const openAddDialog = () => {
    setEditingKey(null);
    setKeyInput("");
    setValueInput("");
    setEditorOpen(true);
  };

  const openEditDialog = (row: EnvironmentVariableRow) => {
    setEditingKey(row.key);
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
    const duplicate = rows.some((row) =>
      row.key !== editingKey && row.key.toLowerCase() === key.toLowerCase()
    );
    if (duplicate) {
      toast.error("Key 已存在，请勿重复添加");
      return;
    }
    const nextRows = editingKey == null
      ? [...rows, { id: nextRowId.current++, key, value: valueInput }]
      : rows.map((row) => row.key === editingKey ? { ...row, key, value: valueInput } : row);
    if (await persistRows(nextRows)) {
      setEditorOpen(false);
      toast.success(editingKey == null ? "变量已添加" : "变量已更新");
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    if (await persistRows(rows.filter((row) => row.id !== deleteTarget.id))) {
      setDeleteTarget(null);
      toast.success("变量已删除");
    }
  };

  const handleRefresh = () => {
    if (selectedAgentId) void loadVariables(selectedAgentId);
  };

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">环境变量</h1>
        {agents.length > 0 && (
          <div className="flex items-center gap-2">
            <Select
              value={selectedAgentId}
              onValueChange={(agentId) => {
                setSelectedAgentId(agentId);
                writeSelectedAgentId(Number(agentId));
              }}
              disabled={agentsLoading || saving}
            >
              <SelectTrigger className="w-[220px]">
                <SelectValue placeholder="选择智能体" />
              </SelectTrigger>
              <SelectContent>
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
              onClick={handleRefresh}
              title="刷新"
              aria-label="刷新"
              disabled={!selectedAgentId || variablesLoading || saving}
            >
              <RefreshCw className={variablesLoading ? "size-4 animate-spin" : "size-4"} />
            </Button>
          </div>
        )}
      </div>

      {agentsLoading ? (
        <div className="py-8 text-center text-muted-foreground">加载智能体中...</div>
      ) : agentsError ? (
        <div className="flex flex-col items-center gap-3 py-8 text-sm">
          <span className="text-destructive">加载智能体失败：{agentsError}</span>
          <Button variant="outline" onClick={() => void loadAgents()}>重试</Button>
        </div>
      ) : agents.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">
          暂无智能体，请先在「智能体管理」中创建
        </div>
      ) : (
        <>
          <div className="flex justify-end">
            <Button
              variant="outline"
              onClick={openAddDialog}
              disabled={!selectedAgentId || variablesLoading || saving || variablesError !== null}
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
              <Button
                variant="outline"
                onClick={() => void loadVariables(selectedAgentId)}
                disabled={!selectedAgentId}
              >
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
                  <TableHead className="w-[35%]">Key</TableHead>
                  <TableHead>Value</TableHead>
                  <TableHead className="w-24 text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell className="break-all font-mono">{row.key}</TableCell>
                    <TableCell className="whitespace-pre-wrap break-all">{row.value}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => openEditDialog(row)}
                          title="编辑变量"
                          aria-label={`编辑变量 ${row.key}`}
                          disabled={saving}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          onClick={() => setDeleteTarget(row)}
                          title="删除变量"
                          aria-label={`删除变量 ${row.key}`}
                          disabled={saving}
                        >
                          <Trash2 className="size-4 text-destructive" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </>
      )}

      <Dialog open={editorOpen} onOpenChange={(open) => !saving && setEditorOpen(open)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingKey == null ? "添加变量" : "编辑变量"}</DialogTitle>
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
