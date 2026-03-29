import React, { useState, useEffect, useRef } from "react";
import { Plus, Edit, Trash2, Upload, X, ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Spinner } from "@/components/ui/spinner";
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
  agentConfigService,
  type AgentConfigDto,
} from "@/services/agent-config";
import { llmService, type LlmConfigDto } from "@/services/llm";
import { mcpConfigService, type McpConfigDto } from "@/services/mcp-config";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuCheckboxItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge";
import { toolService, type ToolDto } from "@/services/tool";

export const AgentManager: React.FC = () => {
  const [agents, setAgents] = useState<AgentConfigDto[]>([]);
  const [llms, setLlms] = useState<LlmConfigDto[]>([]);
  const [tools, setTools] = useState<ToolDto[]>([]);
  const [mcpConfigs, setMcpConfigs] = useState<McpConfigDto[]>([]);
  const [selectedToolIds, setSelectedToolIds] = useState<string[]>([]);
  const [selectedMcpToolIds, setSelectedMcpToolIds] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AgentConfigDto | null>(null);
  const [formData, setFormData] = useState<AgentConfigDto>({
    name: "",
    avatar: "",
    systemPrompt: "",
    enabledTools: "",
    enabledMcpTools: "",
    defaultLlmId: 0,
    workspaceFolder: "",
  });
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingAgentId, setDeletingAgentId] = useState<number | null>(null);

  useEffect(() => {
    loadAgents();
    loadLlms();
    loadTools();
    loadMcpConfigs();
  }, []);

  const loadMcpConfigs = async () => {
    try {
      const response = await mcpConfigService.list();
      if (response.code === 200 && response.data) {
        setMcpConfigs(response.data);
      }
    } catch (error) {
      console.error("加载MCP配置失败", error);
    }
  };

  const loadAgents = async () => {
    try {
      const response = await agentConfigService.list();
      if (response.code === 200 && response.data) {
        setAgents(response.data);
      }
    } catch (error) {
      console.error("加载智能体失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  };

  const loadLlms = async () => {
    try {
      const response = await llmService.list();
      if (response.code === 200 && response.data) {
        setLlms(response.data);
      }
    } catch (error) {
      console.error("加载LLM配置失败", error);
    }
  };

  const loadTools = async () => {
    try {
      const response = await toolService.list();
      if (response.code === 200 && response.data) {
        setTools(response.data);
      }
    } catch (error) {
      console.error("加载工具列表失败", error);
    }
  };

  const handleAvatarChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        alert("图片大小不能超过5MB");
        return;
      }
      if (!file.type.startsWith("image/")) {
        alert("请上传图片文件");
        return;
      }
      setAvatarFile(file);
      const reader = new FileReader();
      reader.onloadend = () => {
        setAvatarPreview(reader.result as string);
      };
      reader.readAsDataURL(file);
    }
  };

  const clearAvatar = () => {
    setAvatarFile(null);
    setAvatarPreview(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = "";
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const workspaceFolder = formData.workspaceFolder?.trim();
      const payload: AgentConfigDto = {
        ...formData,
        workspaceFolder: workspaceFolder ? workspaceFolder : undefined,
        enabledTools: JSON.stringify(selectedToolIds),
        enabledMcpTools: JSON.stringify(selectedMcpToolIds),
      };
      if (editingAgent?.id) {
        const response = await agentConfigService.update(
          editingAgent.id,
          payload,
          avatarFile || undefined,
        );
        if (response.code === 200) {
          loadAgents();
          setIsDialogOpen(false);
          resetForm();
        }
      } else {
        const response = await agentConfigService.create(
          payload,
          avatarFile || undefined,
        );
        if (response.code === 200) {
          loadAgents();
          setIsDialogOpen(false);
          resetForm();
        }
      }
    } catch (error) {
      console.error("保存智能体失败", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = (id: number) => {
    setDeletingAgentId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingAgentId) return;

    try {
      const response = await agentConfigService.delete(deletingAgentId);
      if (response.code === 200) {
        loadAgents();
      }
    } catch (error) {
      console.error("删除智能体失败", error);
    } finally {
      setDeleteDialogOpen(false);
      setDeletingAgentId(null);
    }
  };

  const handleEdit = (agent: AgentConfigDto) => {
    setEditingAgent(agent);
    setFormData(agent);
    setSelectedToolIds(parseEnabledTools(agent.enabledTools));
    setSelectedMcpToolIds(parseEnabledTools(agent.enabledMcpTools));
    setAvatarPreview(agent.avatar || null);
    setAvatarFile(null);
    setIsDialogOpen(true);
  };

  const handleAdd = () => {
    setEditingAgent(null);
    setFormData({
      name: "",
      avatar: "",
      systemPrompt: "",
      enabledTools: "",
      enabledMcpTools: "",
      defaultLlmId: llms[0]?.id || 0,
      workspaceFolder: "",
    });
    setSelectedToolIds([]);
    setSelectedMcpToolIds([]);
    setAvatarPreview(null);
    setAvatarFile(null);
    setIsDialogOpen(true);
  };

  const resetForm = () => {
    setEditingAgent(null);
    setFormData({
      name: "",
      avatar: "",
      systemPrompt: "",
      enabledTools: "",
      enabledMcpTools: "",
      defaultLlmId: 0,
      workspaceFolder: "",
    });
    setSelectedToolIds([]);
    setSelectedMcpToolIds([]);
    setAvatarFile(null);
    setAvatarPreview(null);
  };

  const getLlmName = (llmId: number) => {
    const llm = llms.find((l) => l.id === llmId);
    return llm?.name || "未知";
  };

  const parseEnabledTools = (value?: string) => {
    if (!value) return [];
    try {
      const parsed = JSON.parse(value);
      if (Array.isArray(parsed)) {
        return parsed.filter((item) => typeof item === "string");
      }
    } catch {
      // ignore
    }
    return value
      .split(",")
      .map((item) => item.trim())
      .filter((item) => item.length > 0);
  };

  const toggleTool = (toolId: string) => {
    setSelectedToolIds((prev) =>
      prev.includes(toolId)
        ? prev.filter((id) => id !== toolId)
        : [...prev, toolId],
    );
  };

  const toggleMcpTool = (mcpName: string) => {
    const mcpId = `mcp:${mcpName}`;
    setSelectedMcpToolIds((prev) =>
      prev.includes(mcpId)
        ? prev.filter((id) => id !== mcpId)
        : [...prev, mcpId],
    );
  };

  const getToolLabel = (toolId: string) => {
    const tool = tools.find((t) => t.id === toolId);
    return tool?.name || toolId;
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">智能体</h1>
          <p className="text-sm text-muted-foreground mt-1">配置AI助手的行为和工具</p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4 mr-2" />
              添加智能体
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>
                {editingAgent ? "编辑智能体" : "添加智能体"}
              </DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4 mt-4">
              <div className="space-y-2">
                <Label htmlFor="name">名称</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) =>
                    setFormData({ ...formData, name: e.target.value })
                  }
                  placeholder="例如：助手"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="workspaceFolder">工作区文件夹名（可选）</Label>
                <Input
                  id="workspaceFolder"
                  value={formData.workspaceFolder || ""}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      workspaceFolder: e.target.value,
                    })
                  }
                  placeholder="不填则使用基础目录/智能体名"
                />
                <p className="text-xs text-muted-foreground">
                  仅填写文件夹名，不需要包含路径
                </p>
              </div>
              <div className="space-y-2">
                <Label>头像</Label>
                <div className="flex items-center gap-4">
                  <Avatar className="h-16 w-16">
                    <AvatarImage
                      src={avatarPreview || formData.avatar || undefined}
                    />
                    <AvatarFallback className="text-lg">
                      {formData.name.slice(0, 2).toUpperCase() || "A"}
                    </AvatarFallback>
                  </Avatar>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <Upload className="h-4 w-4 mr-2" />
                      上传头像
                    </Button>
                    {(avatarFile || avatarPreview) && (
                      <Button
                        type="button"
                        variant="outline"
                        onClick={clearAvatar}
                      >
                        <X className="h-4 w-4 mr-2" />
                        清除
                      </Button>
                    )}
                  </div>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    onChange={handleAvatarChange}
                    className="hidden"
                  />
                </div>
                <p className="text-xs text-muted-foreground">
                  支持 JPG、PNG、GIF、WebP 格式，最大 5MB
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="defaultLlmId">默认LLM</Label>
                <Select
                  value={formData.defaultLlmId?.toString()}
                  onValueChange={(value) =>
                    setFormData({ ...formData, defaultLlmId: parseInt(value) })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择LLM" />
                  </SelectTrigger>
                  <SelectContent>
                    {llms.map((llm) => (
                      <SelectItem key={llm.id} value={llm.id?.toString() || ""}>
                        {llm.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="systemPrompt">系统提示词</Label>
                <Textarea
                  id="systemPrompt"
                  value={formData.systemPrompt || ""}
                  onChange={(e) =>
                    setFormData({ ...formData, systemPrompt: e.target.value })
                  }
                  placeholder="你是一个有帮助的助手..."
                  rows={4}
                />
              </div>
              <div className="space-y-2">
                <Label>可用工具</Label>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="outline"
                      className="w-full justify-between"
                    >
                      {selectedToolIds.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {selectedToolIds.slice(0, 3).map((toolId) => (
                            <Badge key={toolId} variant="secondary">
                              {getToolLabel(toolId)}
                            </Badge>
                          ))}
                          {selectedToolIds.length > 3 && (
                            <Badge variant="secondary">
                              +{selectedToolIds.length - 3}
                            </Badge>
                          )}
                        </div>
                      ) : (
                        <span className="text-muted-foreground">选择工具</span>
                      )}
                      <ChevronDown className="h-4 w-4 opacity-60" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    {tools.length === 0 ? (
                      <div className="px-2 py-1.5 text-sm text-muted-foreground">
                        暂无可用工具
                      </div>
                    ) : (
                      tools.map((tool) => (
                        <DropdownMenuCheckboxItem
                          key={tool.id}
                          checked={selectedToolIds.includes(tool.id)}
                          onCheckedChange={() => toggleTool(tool.id)}
                        >
                          <div className="flex flex-col">
                            <span>{tool.name}</span>
                            {tool.description && (
                              <span className="text-xs text-muted-foreground">
                                {tool.description}
                              </span>
                            )}
                          </div>
                        </DropdownMenuCheckboxItem>
                      ))
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
              </div>
              <div className="space-y-2">
                <Label>MCP工具</Label>
                <DropdownMenu>
                  <DropdownMenuTrigger asChild>
                    <Button
                      variant="outline"
                      className="w-full justify-between"
                    >
                      {selectedMcpToolIds.length > 0 ? (
                        <div className="flex flex-wrap gap-1">
                          {selectedMcpToolIds.slice(0, 3).map((mcpId) => (
                            <Badge key={mcpId} variant="outline">
                              {mcpId}
                            </Badge>
                          ))}
                          {selectedMcpToolIds.length > 3 && (
                            <Badge variant="outline">
                              +{selectedMcpToolIds.length - 3}
                            </Badge>
                          )}
                        </div>
                      ) : (
                        <span className="text-muted-foreground">
                          选择MCP工具
                        </span>
                      )}
                      <ChevronDown className="h-4 w-4 opacity-60" />
                    </Button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="start">
                    {mcpConfigs.length === 0 ? (
                      <div className="px-2 py-1.5 text-sm text-muted-foreground">
                        暂无可用MCP配置，请在"MCP配置"中添加
                      </div>
                    ) : (
                      mcpConfigs.map((config) => {
                        const mcpId = `mcp:${config.name}`;
                        return (
                          <DropdownMenuCheckboxItem
                            key={config.id}
                            checked={selectedMcpToolIds.includes(mcpId)}
                            onCheckedChange={() => toggleMcpTool(config.name)}
                          >
                            <div className="flex flex-col">
                              <span>{config.name}</span>
                              <span className="text-xs text-muted-foreground">
                                {config.transportType}
                              </span>
                            </div>
                          </DropdownMenuCheckboxItem>
                        );
                      })
                    )}
                  </DropdownMenuContent>
                </DropdownMenu>
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

      <div className="border rounded-lg">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>头像</TableHead>
              <TableHead>名称</TableHead>
              <TableHead>默认LLM</TableHead>
              <TableHead className="text-center">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isInitialLoading ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center py-12">
                  <Spinner className="size-6 mx-auto" />
                </TableCell>
              </TableRow>
            ) : agents.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={4}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无智能体，点击"添加智能体"创建
                </TableCell>
              </TableRow>
            ) : (
              agents.map((agent) => (
                <TableRow key={agent.id}>
                  <TableCell>
                    <Avatar className="h-8 w-8">
                      <AvatarImage src={agent.avatar} />
                      <AvatarFallback>
                        {agent.name.slice(0, 2).toUpperCase()}
                      </AvatarFallback>
                    </Avatar>
                  </TableCell>
                  <TableCell className="font-medium">{agent.name}</TableCell>
                  <TableCell>{getLlmName(agent.defaultLlmId)}</TableCell>
                  <TableCell className="text-left">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleEdit(agent)}
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => agent.id && handleDelete(agent.id)}
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
              确定要删除这个智能体吗？此操作无法撤销。
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
