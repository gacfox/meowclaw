import { useState, useEffect, useCallback } from "react";
import type { AgentDTO, ScheduledTaskDTO, ScheduledTaskExecutionDTO, ChatEventBatchDTO } from "@/types";
import { listAgents } from "@/services/agent";
import {
  listScheduledTasks,
  createScheduledTask,
  updateScheduledTask,
  deleteScheduledTask as apiDeleteScheduledTask,
  toggleScheduledTask,
  triggerScheduledTask,
  listScheduledTaskExecutions,
} from "@/services/scheduled-task";
import { listBatches } from "@/services/conversation";
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
import { Plus, Pencil, Trash2, Play, History, Power, PowerOff, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";

interface TaskFormData {
  name: string;
  agentId: string;
  userPrompt: string;
  cronExpression: string;
  createNewSession: boolean;
}

const emptyForm: TaskFormData = {
  name: "",
  agentId: "",
  userPrompt: "",
  cronExpression: "",
  createNewSession: false,
};

const statusLabel: Record<string, { label: string; variant: "default" | "secondary" | "destructive" | "outline" }> = {
  SUCCESS: { label: "成功", variant: "default" },
  RUNNING: { label: "执行中", variant: "secondary" },
  ERROR: { label: "失败", variant: "destructive" },
};

function formatTimestamp(ts: number | null): string {
  if (!ts) return "-";
  const d = new Date(ts);
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function ScheduledTaskPage() {
  const [tasks, setTasks] = useState<ScheduledTaskDTO[]>([]);
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [taskList, agentList] = await Promise.all([listScheduledTasks(), listAgents()]);
      setTasks(taskList);
      setAgents(agentList);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const getAgentName = (agentId: number) => {
    return agents.find((a) => a.id === agentId)?.name ?? String(agentId);
  };

  // Create / Edit dialog
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ScheduledTaskDTO | null>(null);
  const [form, setForm] = useState<TaskFormData>(emptyForm);
  const [saving, setSaving] = useState(false);
  const [cronError, setCronError] = useState("");

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setCronError("");
    setDialogOpen(true);
  };

  const openEdit = (task: ScheduledTaskDTO) => {
    setEditing(task);
    setForm({
      name: task.name,
      agentId: task.agentId.toString(),
      userPrompt: task.userPrompt,
      cronExpression: task.cronExpression,
      createNewSession: task.createNewSession,
    });
    setCronError("");
    setDialogOpen(true);
  };

  const handleSave = async () => {
    if (!form.name || !form.agentId || !form.userPrompt || !form.cronExpression) {
      toast.error("请填写所有必填字段");
      return;
    }
    setSaving(true);
    try {
      const data = {
        name: form.name,
        agentId: parseInt(form.agentId),
        userPrompt: form.userPrompt,
        cronExpression: form.cronExpression,
        createNewSession: form.createNewSession,
      };
      if (editing) {
        await updateScheduledTask(editing.id, data);
      } else {
        await createScheduledTask(data);
      }
      await fetchData();
      setDialogOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setSaving(false);
    }
  };

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<ScheduledTaskDTO | null>(null);

  const openDelete = (task: ScheduledTaskDTO) => {
    setDeleting(task);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteScheduledTask(deleting.id);
      await fetchData();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  const handleToggle = async (task: ScheduledTaskDTO) => {
    try {
      await toggleScheduledTask(task.id);
      await fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "操作失败");
    }
  };

  const handleTrigger = async (task: ScheduledTaskDTO) => {
    try {
      await triggerScheduledTask(task.id);
      await fetchData();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "触发失败");
    }
  };

  // Execution log dialog
  const [execDialogOpen, setExecDialogOpen] = useState(false);
  const [execTask, setExecTask] = useState<ScheduledTaskDTO | null>(null);
  const [executions, setExecutions] = useState<ScheduledTaskExecutionDTO[]>([]);
  const [loadingExec, setLoadingExec] = useState(false);
  const [expandedExecId, setExpandedExecId] = useState<number | null>(null);
  const [batchData, setBatchData] = useState<Record<number, ChatEventBatchDTO[]>>({});

  const openExecLog = async (task: ScheduledTaskDTO) => {
    setExecTask(task);
    setExpandedExecId(null);
    setBatchData({});
    setExecDialogOpen(true);
    setLoadingExec(true);
    try {
      const execs = await listScheduledTaskExecutions(task.id);
      setExecutions(execs);
    } finally {
      setLoadingExec(false);
    }
  };

  const toggleExpandExec = async (exec: ScheduledTaskExecutionDTO) => {
    if (expandedExecId === exec.id) {
      setExpandedExecId(null);
      return;
    }
    setExpandedExecId(exec.id);
    if (!batchData[exec.id]) {
      try {
        const batches = await listBatches(exec.conversationId);
        setBatchData((prev) => ({ ...prev, [exec.id]: batches }));
      } catch {
        // ignore
      }
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">定时任务</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            添加任务
          </Button>
          <Button variant="outline" size="icon" onClick={fetchData} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : tasks.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">暂无定时任务，点击「添加任务」创建</div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead>智能体</TableHead>
              <TableHead>Cron 表达式</TableHead>
              <TableHead>会话策略</TableHead>
              <TableHead>启用状态</TableHead>
              <TableHead>上次执行</TableHead>
              <TableHead>上次执行时间</TableHead>
              <TableHead className="text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tasks.map((task) => {
              const st = task.lastStatus ? statusLabel[task.lastStatus] : null;
              return (
                <TableRow key={task.id}>
                  <TableCell className="font-medium">{task.name}</TableCell>
                  <TableCell>{getAgentName(task.agentId)}</TableCell>
                  <TableCell><code className="text-xs">{task.cronExpression}</code></TableCell>
                  <TableCell>{task.createNewSession ? "每次新会话" : "绑定会话"}</TableCell>
                  <TableCell>
                    {task.enabled ? (
                      <Badge variant="outline">已启用</Badge>
                    ) : (
                      <Badge variant="secondary">已禁用</Badge>
                    )}
                  </TableCell>
                  <TableCell>
                    {st ? (
                      <Badge variant={st.variant}>{st.label}</Badge>
                    ) : (
                      <span className="text-muted-foreground">-</span>
                    )}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {formatTimestamp(task.lastExecutedAt)}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button variant="ghost" size="icon-sm" onClick={() => handleToggle(task)} title={task.enabled ? "禁用" : "启用"}>
                        {task.enabled ? <PowerOff className="size-4" /> : <Power className="size-4" />}
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => handleTrigger(task)} title="立即触发">
                        <Play className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => openExecLog(task)} title="执行日志">
                        <History className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => openEdit(task)} title="编辑">
                        <Pencil className="size-4" />
                      </Button>
                      <Button variant="ghost" size="icon-sm" onClick={() => openDelete(task)} title="删除">
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

      {/* Create / Edit Dialog */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="max-h-[85vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing ? "编辑定时任务" : "添加定时任务"}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>任务名称</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="输入任务名称" />
            </div>
            <div className="flex flex-col gap-2">
              <Label>智能体</Label>
              <Select value={form.agentId} onValueChange={(v) => setForm({ ...form, agentId: v })}>
                <SelectTrigger className="w-full">
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
            </div>
            <div className="flex flex-col gap-2">
              <Label>用户提示词</Label>
              <Textarea
                value={form.userPrompt}
                onChange={(e) => setForm({ ...form, userPrompt: e.target.value })}
                rows={4}
                placeholder="定时触发时发送给智能体的内容"
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>Cron 表达式</Label>
              <Input
                value={form.cronExpression}
                onChange={(e) => {
                  setForm({ ...form, cronExpression: e.target.value });
                  setCronError("");
                }}
                placeholder="0 0 * * *（每天零点）"
              />
              {cronError && <p className="text-xs text-destructive">{cronError}</p>}
              <p className="text-xs text-muted-foreground">
                格式：秒 分 时 日 月 周，例如 <code>0 0 9 * * *</code> 每天9点
              </p>
            </div>
            <div className="flex flex-col gap-2">
              <Label>会话策略</Label>
              <RadioGroup
                value={form.createNewSession ? "true" : "false"}
                onValueChange={(v) => setForm({ ...form, createNewSession: v === "true" })}
                className="flex gap-4"
              >
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="false" id="bind" />
                  <Label htmlFor="bind">绑定会话</Label>
                </div>
                <div className="flex items-center gap-2">
                  <RadioGroupItem value="true" id="new" />
                  <Label htmlFor="new">每次新会话</Label>
                </div>
              </RadioGroup>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>取消</Button>
            <Button onClick={handleSave} disabled={saving || !form.name || !form.agentId || !form.cronExpression}>
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
              确定要删除定时任务「{deleting?.name}」吗？相关的执行记录也将一并删除。此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Execution Log Dialog */}
      <Dialog open={execDialogOpen} onOpenChange={setExecDialogOpen}>
        <DialogContent className="max-h-[85vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>执行日志 — {execTask?.name}</DialogTitle>
          </DialogHeader>
          {loadingExec ? (
            <div className="py-4 text-center text-muted-foreground">加载中...</div>
          ) : executions.length === 0 ? (
            <div className="py-4 text-center text-muted-foreground">暂无执行记录</div>
          ) : (
            <div className="flex flex-col gap-2">
              {executions.map((exec) => {
                const st = statusLabel[exec.status];
                const batches = batchData[exec.id];
                const expanded = expandedExecId === exec.id;
                return (
                  <div key={exec.id} className="rounded-md border">
                    <button
                      type="button"
                      className="flex w-full items-center gap-3 px-4 py-2 text-left text-sm hover:bg-muted/50"
                      onClick={() => toggleExpandExec(exec)}
                    >
                      <Badge variant={st?.variant ?? "outline"} className="shrink-0">{st?.label ?? exec.status}</Badge>
                      <span className="text-muted-foreground">{formatTimestamp(exec.executedAt)}</span>
                      <span className="text-xs text-muted-foreground">会话 #{exec.conversationId}</span>
                    </button>
                    {expanded && batches && (
                      <div className="border-t px-4 py-2">
                        {batches.length === 0 ? (
                          <p className="text-xs text-muted-foreground">暂无会话内容</p>
                        ) : (
                          batches.map((batch) => (
                            <div key={batch.id} className="mb-3 last:mb-0">
                              <div className="mb-1 text-xs font-medium text-muted-foreground">
                                用户: {batch.userContent}
                              </div>
                              {batch.events
                                .filter((e) => e.type === "FINAL_ANSWER" && e.content)
                                .map((e, i) => (
                                  <div key={i} className="whitespace-pre-wrap text-sm">
                                    {e.content}
                                  </div>
                                ))}
                              {batch.inputTokens != null && batch.outputTokens != null && (
                                <div className="mt-1 text-xs text-muted-foreground">
                                  ↑{batch.inputTokens} ↓{batch.outputTokens}
                                </div>
                              )}
                            </div>
                          ))
                        )}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
