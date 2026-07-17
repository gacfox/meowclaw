import { useState, useEffect, useCallback, useRef } from "react";
import type { AgentDTO, LlmDTO, McpToolDTO, ToolInfoDTO } from "@/types";
import { listAgents, createAgent, updateAgent, deleteAgent as apiDeleteAgent, uploadAgentAvatar } from "@/services/agent";
import { listLlms } from "@/services/llm";
import { listTools } from "@/services/tool";
import { listMcpTools } from "@/services/mcp-service";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
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
import { Switch } from "@/components/ui/switch";
import { Plus, Pencil, Trash2, Copy, FolderOpen, Camera, RefreshCw } from "lucide-react";
import { toast } from "sonner";

function parseJsonArray(raw: string | null): string[] {
  if (!raw) return [];
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function toJsonArray(arr: string[]): string | undefined {
  return arr.length > 0 ? JSON.stringify(arr) : undefined;
}

interface AgentFormData {
  name: string;
  persona: string;
  enabledTools: string[];
  enabledMcpTools: string[];
  llmId: string;
  workspaceFolder: string;
}

const emptyForm: AgentFormData = {
  name: "",
  persona: "",
  enabledTools: [],
  enabledMcpTools: [],
  llmId: "",
  workspaceFolder: "",
};

export function AgentConfigPage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [llms, setLlms] = useState<LlmDTO[]>([]);
  const [tools, setTools] = useState<ToolInfoDTO[]>([]);
  const [mcpTools, setMcpTools] = useState<McpToolDTO[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploadingAvatarId, setUploadingAvatarId] = useState<number | null>(null);
  const avatarFileRef = useRef<HTMLInputElement>(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [agentList, llmList, toolList, mcpToolList] = await Promise.all([
        listAgents(),
        listLlms(),
        listTools(),
        listMcpTools(),
      ]);
      setAgents(agentList);
      setLlms(llmList);
      setTools(toolList);
      setMcpTools(mcpToolList);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Create / Edit dialog
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<AgentDTO | null>(null);
  const [form, setForm] = useState<AgentFormData>(emptyForm);
  const [saving, setSaving] = useState(false);

  const openCreate = () => {
    setEditing(null);
    setForm(emptyForm);
    setDialogOpen(true);
  };

  const openEdit = (agent: AgentDTO) => {
    setEditing(agent);
    setForm({
      name: agent.name,
      persona: agent.persona ?? "",
      enabledTools: parseJsonArray(agent.enabledTools),
      enabledMcpTools: parseJsonArray(agent.enabledMcpTools),
      llmId: agent.llmId?.toString() ?? "",
      workspaceFolder: agent.workspaceFolder ?? "",
    });
    setDialogOpen(true);
  };

  const toggleTool = (toolName: string) => {
    setForm((prev) => ({
      ...prev,
      enabledTools: prev.enabledTools.includes(toolName)
        ? prev.enabledTools.filter((t) => t !== toolName)
        : [...prev.enabledTools, toolName],
    }));
  };

  const toggleMcpTool = (toolName: string) => {
    setForm((prev) => ({
      ...prev,
      enabledMcpTools: prev.enabledMcpTools.includes(toolName)
        ? prev.enabledMcpTools.filter((t) => t !== toolName)
        : [...prev.enabledMcpTools, toolName],
    }));
  };

  const handleSave = async () => {
    setSaving(true);
    try {
      const data = {
        name: form.name,
        persona: form.persona || undefined,
        enabledTools: toJsonArray(form.enabledTools),
        enabledMcpTools: toJsonArray(form.enabledMcpTools),
        llmId: form.llmId ? parseInt(form.llmId) : undefined,
        workspaceFolder: form.workspaceFolder || undefined,
      };
      if (editing) {
        await updateAgent(editing.id, data);
      } else {
        await createAgent(data);
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
  const [deleting, setDeleting] = useState<AgentDTO | null>(null);

  const openDelete = (agent: AgentDTO) => {
    setDeleting(agent);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteAgent(deleting.id);
      await fetchData();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  // Copy dialog
  const [copyDialogOpen, setCopyDialogOpen] = useState(false);
  const [copyingAgent, setCopyingAgent] = useState<AgentDTO | null>(null);
  const [copyName, setCopyName] = useState("");
  const [copyIndependentWorkspace, setCopyIndependentWorkspace] = useState(false);
  const [copySaving, setCopySaving] = useState(false);

  const openCopy = (agent: AgentDTO) => {
    setCopyingAgent(agent);
    setCopyName(`${agent.name}(复制)`);
    setCopyIndependentWorkspace(true);
    setCopyDialogOpen(true);
  };

  const handleCopy = async () => {
    if (!copyingAgent || !copyName.trim()) return;
    setCopySaving(true);
    try {
      await createAgent({
        name: copyName.trim(),
        avatarUrl: copyingAgent.avatarUrl ?? undefined,
        persona: copyingAgent.persona ?? undefined,
        enabledTools: copyingAgent.enabledTools ?? undefined,
        enabledMcpTools: copyingAgent.enabledMcpTools ?? undefined,
        llmId: copyingAgent.llmId ?? undefined,
        workspaceFolder: copyIndependentWorkspace ? undefined : (copyingAgent.workspaceFolder ?? undefined),
      });
      await fetchData();
      setCopyDialogOpen(false);
      toast.success("复制成功");
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "复制失败");
    } finally {
      setCopySaving(false);
    }
  };

  const handleAvatarClick = (agentId: number) => {
    avatarFileRef.current?.click();
    setUploadingAvatarId(agentId);
  };

  const handleAvatarChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !uploadingAvatarId) return;
    try {
      const avatarUrl = await uploadAgentAvatar(uploadingAvatarId, file);
      setAgents((prev) => prev.map((a) => a.id === uploadingAvatarId ? { ...a, avatarUrl } : a));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "上传失败");
    } finally {
      setUploadingAvatarId(null);
      e.target.value = "";
    }
  };

  const getLlmName = (llmId: number | null) => {
    if (!llmId) return null;
    const llm = llms.find((l) => l.id === llmId);
    return llm?.name ?? null;
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">智能体管理</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openCreate}>
            <Plus className="mr-1 size-4" />
            添加智能体
          </Button>
          <Button variant="outline" size="icon" onClick={fetchData} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : agents.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">暂无智能体，点击「添加智能体」创建</div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {agents.map((agent) => (
            <Card key={agent.id}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
                <div className="flex items-center gap-2">
                  <div className="relative">
                    <Avatar className="size-8">
                      <AvatarImage src={agent.avatarUrl ?? undefined} />
                      <AvatarFallback className="text-xs">
                        {agent.name[0]?.toUpperCase() ?? "A"}
                      </AvatarFallback>
                    </Avatar>
                    <button
                      type="button"
                      onClick={() => handleAvatarClick(agent.id)}
                      disabled={uploadingAvatarId === agent.id}
                      className="absolute -right-1 -bottom-1 flex size-4 items-center justify-center rounded-full bg-primary text-primary-foreground hover:bg-primary/80 disabled:opacity-50"
                    >
                      <Camera className="size-2.5" />
                    </button>
                  </div>
                  <CardTitle className="text-base">{agent.name}</CardTitle>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openEdit(agent)}>
                    <Pencil className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openCopy(agent)} title="复制">
                    <Copy className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openDelete(agent)}>
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                <div className="flex flex-wrap gap-1">
                  {parseJsonArray(agent.enabledTools).map((toolName) => (
                    <Badge key={toolName} variant="outline" className="text-xs">
                      {toolName}
                    </Badge>
                  ))}
                </div>
                {getLlmName(agent.llmId) && (
                  <Badge variant="secondary">{getLlmName(agent.llmId)}</Badge>
                )}
                {agent.workspaceFolder && (
                  <div className="flex items-center gap-1 text-xs text-muted-foreground">
                    <FolderOpen className="size-3" />
                    <span className="truncate">{agent.workspaceFolder}</span>
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
            <DialogTitle>{editing ? "编辑智能体" : "添加智能体"}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>智能体名称</Label>
              <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
            </div>
            <div className="flex flex-col gap-2">
              <Label>人设</Label>
              <Textarea
                value={form.persona}
                onChange={(e) => setForm({ ...form, persona: e.target.value })}
                rows={5}
                placeholder="描述该智能体的角色与风格..."
              />
            </div>
            <div className="flex flex-col gap-2">
              <Label>启用内置工具</Label>
              <div className="flex flex-wrap gap-2">
                {tools.map((tool) => (
                  <Button
                    key={tool.name}
                    type="button"
                    size="sm"
                    variant={form.enabledTools.includes(tool.name) ? "default" : "outline"}
                    onClick={() => toggleTool(tool.name)}
                  >
                    {tool.name}
                  </Button>
                ))}
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <Label>启用 MCP 工具</Label>
              {mcpTools.length === 0 ? (
                <p className="text-xs text-muted-foreground">暂无可用的 MCP 工具，请先在「MCP 服务」中启用服务</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {mcpTools.map((tool) => (
                    <Button
                      key={tool.name}
                      type="button"
                      size="sm"
                      variant={form.enabledMcpTools.includes(tool.name) ? "default" : "outline"}
                      onClick={() => toggleMcpTool(tool.name)}
                      title={tool.description || tool.name}
                    >
                      {tool.name}
                    </Button>
                  ))}
                </div>
              )}
            </div>
            <div className="flex flex-col gap-2">
              <Label>关联 LLM</Label>
              <Select value={form.llmId} onValueChange={(v) => setForm({ ...form, llmId: v })}>
                <SelectTrigger className="w-full">
                  <SelectValue placeholder="选择 LLM" />
                </SelectTrigger>
                <SelectContent>
                  {llms.map((llm) => (
                    <SelectItem key={llm.id} value={llm.id.toString()}>
                      {llm.name} ({llm.model})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex flex-col gap-2">
              <Label>工作区路径</Label>
              <Input
                value={form.workspaceFolder}
                onChange={(e) => setForm({ ...form, workspaceFolder: e.target.value })}
                placeholder="留空则自动生成"
              />
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

      {/* Delete Dialog */}
      <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除智能体「{deleting?.name}」吗？此操作不可撤销。
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
            <DialogTitle>复制智能体</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-2">
              <Label>新智能体名称</Label>
              <Input
                value={copyName}
                onChange={(e) => setCopyName(e.target.value)}
                placeholder="请输入新智能体名称"
                autoFocus
              />
            </div>
            <div className="flex items-center justify-between gap-4">
              <Label htmlFor="independent-workspace" className="cursor-pointer">生成独立工作区</Label>
              <Switch
                id="independent-workspace"
                checked={copyIndependentWorkspace}
                onCheckedChange={setCopyIndependentWorkspace}
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

      <input
        ref={avatarFileRef}
        type="file"
        accept="image/*"
        className="hidden"
        onChange={handleAvatarChange}
      />
    </div>
  );
}
