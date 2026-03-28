import React, { useState, useEffect } from "react";
import { Plus, Edit, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
import { mcpConfigService, type McpConfigDto } from "@/services/mcp-config";
import { Badge } from "@/components/ui/badge";

const TRANSPORT_OPTIONS = [
  { value: "stdio", label: "STDIO (标准输入输出)" },
  { value: "streamable_http", label: "Streamable HTTP" },
  { value: "sse", label: "SSE (Server-Sent Events)" },
];

export const McpManager: React.FC = () => {
  const [configs, setConfigs] = useState<McpConfigDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingConfig, setEditingConfig] = useState<McpConfigDto | null>(null);
  const [formData, setFormData] = useState<McpConfigDto>({
    name: "",
    transportType: "stdio",
    command: "",
    args: "",
    envVars: "",
    url: "",
  });
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingConfigId, setDeletingConfigId] = useState<number | null>(null);

  useEffect(() => {
    loadConfigs();
  }, []);

  const loadConfigs = async () => {
    try {
      const response = await mcpConfigService.list();
      if (response.code === 200 && response.data) {
        setConfigs(response.data);
      }
    } catch (error) {
      console.error("加载MCP配置失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const payload: McpConfigDto = {
        name: formData.name,
        transportType: formData.transportType,
        command:
          formData.transportType === "stdio" ? formData.command : undefined,
        args:
          formData.transportType === "stdio" && formData.args
            ? formData.args
            : undefined,
        envVars:
          formData.transportType === "stdio" && formData.envVars
            ? formData.envVars
            : undefined,
        url:
          formData.transportType === "streamable_http" ||
          formData.transportType === "sse"
            ? formData.url
            : undefined,
      };

      if (editingConfig?.id) {
        const response = await mcpConfigService.update(
          editingConfig.id,
          payload,
        );
        if (response.code === 200) {
          loadConfigs();
          setIsDialogOpen(false);
          resetForm();
        }
      } else {
        const response = await mcpConfigService.create(payload);
        if (response.code === 200) {
          loadConfigs();
          setIsDialogOpen(false);
          resetForm();
        }
      }
    } catch (error) {
      console.error("保存MCP配置失败", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = (id: number) => {
    setDeletingConfigId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingConfigId) return;

    try {
      const response = await mcpConfigService.delete(deletingConfigId);
      if (response.code === 200) {
        loadConfigs();
      }
    } catch (error) {
      console.error("删除MCP配置失败", error);
    } finally {
      setDeleteDialogOpen(false);
      setDeletingConfigId(null);
    }
  };

  const handleEdit = (config: McpConfigDto) => {
    setEditingConfig(config);
    setFormData({
      name: config.name,
      transportType: config.transportType,
      command: config.command || "",
      args: config.args || "",
      envVars: config.envVars || "",
      url: config.url || "",
    });
    setIsDialogOpen(true);
  };

  const handleAdd = () => {
    setEditingConfig(null);
    setFormData({
      name: "",
      transportType: "stdio",
      command: "",
      args: "",
      envVars: "",
      url: "",
    });
    setIsDialogOpen(true);
  };

  const resetForm = () => {
    setEditingConfig(null);
    setFormData({
      name: "",
      transportType: "stdio",
      command: "",
      args: "",
      envVars: "",
      url: "",
    });
  };

  const getTransportLabel = (type: string) => {
    const option = TRANSPORT_OPTIONS.find((o) => o.value === type);
    return option?.label || type;
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">MCP配置</h1>
          <p className="text-sm text-muted-foreground mt-1">
            配置MCP服务器供智能体调用
          </p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4 mr-2" />
              添加MCP配置
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-lg max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>
                {editingConfig ? "编辑MCP配置" : "添加MCP配置"}
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
                  placeholder="例如：filesystem, brave-search"
                  required
                />
                <p className="text-xs text-muted-foreground">
                  名称必须唯一，用于在智能体配置中引用
                </p>
              </div>

              <div className="space-y-2">
                <Label htmlFor="transportType">传输类型</Label>
                <Select
                  value={formData.transportType}
                  onValueChange={(value) =>
                    setFormData({ ...formData, transportType: value })
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="选择传输类型" />
                  </SelectTrigger>
                  <SelectContent>
                    {TRANSPORT_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {formData.transportType === "stdio" && (
                <>
                  <div className="space-y-2">
                    <Label htmlFor="command">命令</Label>
                    <Input
                      id="command"
                      value={formData.command}
                      onChange={(e) =>
                        setFormData({ ...formData, command: e.target.value })
                      }
                      placeholder="例如：npx, node"
                      required
                    />
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="args">参数 (JSON数组)</Label>
                    <Input
                      id="args"
                      value={formData.args}
                      onChange={(e) =>
                        setFormData({ ...formData, args: e.target.value })
                      }
                      placeholder='例如：["-y", "@modelcontextprotocol/server-filesystem"]'
                    />
                    <p className="text-xs text-muted-foreground">
                      填写JSON数组格式，如 ["-y", "server-xxx"]
                    </p>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="envVars">环境变量 (JSON对象)</Label>
                    <Input
                      id="envVars"
                      value={formData.envVars}
                      onChange={(e) =>
                        setFormData({ ...formData, envVars: e.target.value })
                      }
                      placeholder='例如：{"KEY": "value"}'
                    />
                  </div>
                </>
              )}

              {(formData.transportType === "streamable_http" ||
                formData.transportType === "sse") && (
                <div className="space-y-2">
                  <Label htmlFor="url">服务器URL</Label>
                  <Input
                    id="url"
                    value={formData.url}
                    onChange={(e) =>
                      setFormData({ ...formData, url: e.target.value })
                    }
                    placeholder="例如：http://localhost:3000"
                    required
                  />
                </div>
              )}

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
              <TableHead>名称</TableHead>
              <TableHead>传输类型</TableHead>
              <TableHead>配置详情</TableHead>
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
            ) : configs.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={4}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无MCP配置，点击"添加MCP配置"创建
                </TableCell>
              </TableRow>
            ) : (
              configs.map((config) => (
                <TableRow key={config.id}>
                  <TableCell className="font-medium">
                    <Badge variant="outline" className="text-base">
                      mcp:{config.name}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    {getTransportLabel(config.transportType)}
                  </TableCell>
                  <TableCell className="text-sm text-muted-foreground">
                    {config.transportType === "stdio" ? (
                      <div>
                        {config.command} {config.args && `(${config.args})`}
                      </div>
                    ) : (
                      <div>{config.url}</div>
                    )}
                  </TableCell>
                  <TableCell className="text-left">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleEdit(config)}
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => config.id && handleDelete(config.id)}
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
              确定要删除这个MCP配置吗？此操作无法撤销。
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
