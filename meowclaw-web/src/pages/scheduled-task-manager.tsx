import React, { useState, useEffect, useCallback } from "react";
import { Plus, Edit, Trash2, Play, Power, PowerOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { Textarea } from "@/components/ui/textarea";
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
  DialogHeader,
  DialogTitle,
  DialogTrigger,
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  scheduledTaskService,
  type ScheduledTaskDto,
} from "@/services/scheduled-task";
import {
  agentConfigService,
  type AgentConfigDto,
} from "@/services/agent-config";
import { toast } from "sonner";

export const ScheduledTaskManager: React.FC = () => {
  const [tasks, setTasks] = useState<ScheduledTaskDto[]>([]);
  const [agents, setAgents] = useState<AgentConfigDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingTask, setEditingTask] = useState<ScheduledTaskDto | null>(null);
  const [formData, setFormData] = useState<ScheduledTaskDto>({
    name: "",
    agentConfigId: 0,
    userPrompt: "",
    cronExpression: "0 0 9 * * ?",
    newSessionEach: true,
    enabled: true,
  });
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingTaskId, setDeletingTaskId] = useState<number | null>(null);
  const [cronPreview, setCronPreview] = useState<string>("");

  const loadTasks = useCallback(async () => {
    try {
      const response = await scheduledTaskService.list();
      if (response.code === 200 && response.data) {
        setTasks(response.data.items);
      }
    } catch (error) {
      console.error("加载定时任务失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  }, []);

  const loadAgents = useCallback(async () => {
    try {
      const response = await agentConfigService.list();
      if (response.code === 200 && response.data) {
        setAgents(response.data);
      }
    } catch (error) {
      console.error("加载智能体列表失败", error);
    }
  }, []);

  useEffect(() => {
    loadTasks();
    loadAgents();
  }, [loadTasks, loadAgents]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      if (editingTask?.id) {
        const response = await scheduledTaskService.update(
          editingTask.id,
          formData,
        );
        if (response.code === 200) {
          loadTasks();
          setIsDialogOpen(false);
          resetForm();
          toast.success("定时任务已更新");
        }
      } else {
        const response = await scheduledTaskService.create(formData);
        if (response.code === 200) {
          loadTasks();
          setIsDialogOpen(false);
          resetForm();
          toast.success("定时任务已创建");
        }
      }
    } catch (error) {
      console.error("保存定时任务失败", error);
      toast.error("保存失败");
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = (id: number) => {
    setDeletingTaskId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingTaskId) return;

    try {
      const response = await scheduledTaskService.delete(deletingTaskId);
      if (response.code === 200) {
        loadTasks();
        toast.success("定时任务已删除");
      }
    } catch (error) {
      console.error("删除定时任务失败", error);
      toast.error("删除失败");
    } finally {
      setDeleteDialogOpen(false);
      setDeletingTaskId(null);
    }
  };

  const handleToggleEnabled = async (task: ScheduledTaskDto) => {
    try {
      const response = await scheduledTaskService.toggleEnabled(task.id!);
      if (response.code === 200) {
        loadTasks();
        toast.success(task.enabled ? "已禁用" : "已启用");
      }
    } catch (error) {
      console.error("切换状态失败", error);
      toast.error("操作失败");
    }
  };

  const handleTrigger = async (task: ScheduledTaskDto) => {
    try {
      const response = await scheduledTaskService.trigger(task.id!);
      if (response.code === 200) {
        loadTasks();
        toast.success("已触发执行");
      }
    } catch (error) {
      console.error("触发执行失败", error);
      toast.error("触发失败");
    }
  };

  const handleEdit = (task: ScheduledTaskDto) => {
    setEditingTask(task);
    setFormData({
      name: task.name,
      agentConfigId: task.agentConfigId,
      userPrompt: task.userPrompt,
      cronExpression: task.cronExpression,
      newSessionEach: task.newSessionEach,
      enabled: task.enabled,
    });
    setIsDialogOpen(true);
  };

  const handleAdd = () => {
    setEditingTask(null);
    setFormData({
      name: "",
      agentConfigId: agents.length > 0 ? agents[0].id! : 0,
      userPrompt: "",
      cronExpression: "0 0 9 * * ?",
      newSessionEach: true,
      enabled: true,
    });
    setCronPreview("");
    setIsDialogOpen(true);
  };

  const resetForm = () => {
    setEditingTask(null);
    setCronPreview("");
  };

  const handleCronChange = async (cronExpression: string) => {
    setFormData({ ...formData, cronExpression });
    try {
      const response =
        await scheduledTaskService.getNextExecution(cronExpression);
      if (response.code === 200) {
        setCronPreview(response.data || "");
      }
    } catch {
      setCronPreview("表达式无效");
    }
  };

  const formatLastExecuted = (timestamp?: number) => {
    if (!timestamp) return "-";
    return new Date(timestamp).toLocaleString("zh-CN");
  };

  return (
    <div className="p-6 h-full flex flex-col min-h-0">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">定时任务</h1>
          <p className="text-sm text-muted-foreground mt-1">
            管理定时执行的智能体任务
          </p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4 mr-2" />
              创建任务
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>
                {editingTask ? "编辑定时任务" : "创建定时任务"}
              </DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4 mt-4">
              <div className="space-y-2">
                <Label htmlFor="name">任务名称</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                  placeholder="例如：每日早报"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="agent">执行智能体</Label>
                <Select
                  value={formData.agentConfigId.toString()}
                  onValueChange={(value) =>
                    setFormData({ ...formData, agentConfigId: parseInt(value) })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择智能体" />
                  </SelectTrigger>
                  <SelectContent>
                    {agents.map((agent) => (
                      <SelectItem key={agent.id} value={agent.id!.toString()}>
                        {agent.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="cronExpression">Cron表达式</Label>
                <Input
                  id="cronExpression"
                  value={formData.cronExpression}
                  onChange={(e) => handleCronChange(e.target.value)}
                  placeholder="0 0 9 * * ?"
                  required
                />
                {cronPreview && (
                  <p className="text-xs text-muted-foreground">
                    下次执行: {cronPreview}
                  </p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="userPrompt">用户提示词</Label>
                <Textarea
                  id="userPrompt"
                  value={formData.userPrompt}
                  onChange={(e) =>
                    setFormData({ ...formData, userPrompt: e.target.value })
                  }
                  placeholder="请帮我..."
                  required
                  rows={4}
                />
              </div>
              <div className="flex items-center justify-between">
                <div className="space-y-0.5">
                  <Label>每次新会话</Label>
                  <p className="text-xs text-muted-foreground">
                    开启后每次执行创建新会话，关闭则在同一会话中连续执行
                  </p>
                </div>
                <Switch
                  checked={formData.newSessionEach}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, newSessionEach: checked })
                  }
                />
              </div>
              <div className="flex items-center justify-between">
                <Label>启用任务</Label>
                <Switch
                  checked={formData.enabled}
                  onCheckedChange={(checked) =>
                    setFormData({ ...formData, enabled: checked })
                  }
                />
              </div>
              <div className="flex justify-end gap-2 pt-4">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsDialogOpen(false)}
                >
                  取消
                </Button>
                <Button type="submit" disabled={isLoading}>
                  {isLoading ? "保存中..." : "保存"}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <div className="border rounded-lg flex-1 overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="text-center">名称</TableHead>
              <TableHead className="text-center">智能体</TableHead>
              <TableHead className="text-center">Cron表达式</TableHead>
              <TableHead className="text-center">状态</TableHead>
              <TableHead className="text-center">上次执行</TableHead>
              <TableHead className="text-center">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isInitialLoading ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-12">
                  <Spinner className="size-6 mx-auto" />
                </TableCell>
              </TableRow>
            ) : tasks.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={6}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无定时任务，点击"创建任务"添加
                </TableCell>
              </TableRow>
            ) : (
              tasks.map((task) => (
                <TableRow key={task.id}>
                  <TableCell className="text-center font-medium">
                    {task.name}
                  </TableCell>
                  <TableCell className="text-center">
                    {task.agentName}
                  </TableCell>
                  <TableCell className="text-center font-mono text-sm">
                    {task.cronExpression}
                  </TableCell>
                  <TableCell className="text-center">
                    <span
                      className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${
                        task.enabled
                          ? "bg-green-100 text-green-800"
                          : "bg-gray-100 text-gray-800"
                      }`}
                    >
                      {task.enabled ? "已启用" : "已禁用"}
                    </span>
                  </TableCell>
                  <TableCell className="text-center text-sm">
                    {formatLastExecuted(task.lastExecutedAt)}
                  </TableCell>
                  <TableCell className="text-center">
                    <Button
                      variant="ghost"
                      size="icon"
                      title={task.enabled ? "禁用" : "启用"}
                      onClick={() => handleToggleEnabled(task)}
                    >
                      {task.enabled ? (
                        <PowerOff className="h-4 w-4" />
                      ) : (
                        <Power className="h-4 w-4" />
                      )}
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      title="立即执行"
                      onClick={() => handleTrigger(task)}
                    >
                      <Play className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleEdit(task)}
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => task.id && handleDelete(task.id)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除这个定时任务吗？此操作无法撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};
