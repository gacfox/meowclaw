import React, { useState, useEffect } from "react";
import { Plus, Edit, Trash2, Eye, EyeOff } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
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
import { llmService, type LlmConfigDto } from "@/services/llm";

export const LlmManager: React.FC = () => {
  const [llms, setLlms] = useState<LlmConfigDto[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingLlm, setEditingLlm] = useState<LlmConfigDto | null>(null);
  const [formData, setFormData] = useState<LlmConfigDto>({
    name: "",
    apiUrl: "",
    apiKey: "",
    model: "",
    maxContextLength: 4096,
    temperature: 0.7,
  });
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingLlmId, setDeletingLlmId] = useState<number | null>(null);
  const [showApiKey, setShowApiKey] = useState(false);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<boolean | null>(null);

  useEffect(() => {
    loadLlms();
  }, []);

  const loadLlms = async () => {
    try {
      const response = await llmService.list();
      if (response.code === 200 && response.data) {
        setLlms(response.data);
      }
    } catch (error) {
      console.error("加载LLM配置失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      if (editingLlm?.id) {
        const response = await llmService.update(editingLlm.id, formData);
        if (response.code === 200) {
          loadLlms();
          setIsDialogOpen(false);
          resetForm();
        }
      } else {
        const response = await llmService.create(formData);
        if (response.code === 200) {
          loadLlms();
          setIsDialogOpen(false);
          resetForm();
        }
      }
    } catch (error) {
      console.error("保存LLM配置失败", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDelete = (id: number) => {
    setDeletingLlmId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingLlmId) return;

    try {
      const response = await llmService.delete(deletingLlmId);
      if (response.code === 200) {
        loadLlms();
      }
    } catch (error) {
      console.error("删除LLM配置失败", error);
    } finally {
      setDeleteDialogOpen(false);
      setDeletingLlmId(null);
    }
  };

  const handleTest = async () => {
    if (!formData.apiUrl || !formData.apiKey || !formData.model) {
      return;
    }
    setIsTesting(true);
    setTestResult(null);

    try {
      const response = await llmService.test(formData);
      setTestResult(response.code === 200 && response.data === true);
    } catch (error) {
      console.error("测试连接失败", error);
      setTestResult(false);
    } finally {
      setIsTesting(false);
    }
  };

  const handleEdit = (llm: LlmConfigDto) => {
    setEditingLlm(llm);
    setFormData(llm);
    setIsDialogOpen(true);
  };

  const handleAdd = () => {
    setEditingLlm(null);
    setFormData({
      name: "",
      apiUrl: "",
      apiKey: "",
      model: "",
      maxContextLength: 4096,
      temperature: 0.7,
    });
    setIsDialogOpen(true);
  };

  const resetForm = () => {
    setEditingLlm(null);
    setFormData({
      name: "",
      apiUrl: "",
      apiKey: "",
      model: "",
      maxContextLength: 4096,
      temperature: 0.7,
    });
    setShowApiKey(false);
    setTestResult(null);
  };

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">大语言模型</h1>
          <p className="text-sm text-muted-foreground mt-1">管理AI模型API配置</p>
        </div>
        <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
          <DialogTrigger asChild>
            <Button onClick={handleAdd}>
              <Plus className="h-4 w-4 mr-2" />
              添加配置
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>
                {editingLlm ? "编辑LLM配置" : "添加LLM配置"}
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
                  placeholder="例如：OpenAI GPT-4"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="apiUrl">API地址</Label>
                <Input
                  id="apiUrl"
                  value={formData.apiUrl}
                  onChange={(e) =>
                    setFormData({ ...formData, apiUrl: e.target.value })
                  }
                  placeholder="https://api.openai.com/v1/chat/completions"
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="apiKey">API密钥</Label>
                <div className="relative">
                  <Input
                    id="apiKey"
                    type={showApiKey ? "text" : "password"}
                    value={formData.apiKey}
                    onChange={(e) =>
                      setFormData({ ...formData, apiKey: e.target.value })
                    }
                    placeholder="sk-..."
                    required
                    className="pr-10"
                  />
                  <button
                    type="button"
                    onClick={() => setShowApiKey(!showApiKey)}
                    className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    {showApiKey ? (
                      <EyeOff className="h-4 w-4" />
                    ) : (
                      <Eye className="h-4 w-4" />
                    )}
                  </button>
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="model">模型</Label>
                <Input
                  id="model"
                  value={formData.model}
                  onChange={(e) =>
                    setFormData({ ...formData, model: e.target.value })
                  }
                  placeholder="gpt-4"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="maxContextLength">最大上下文长度</Label>
                  <Input
                    id="maxContextLength"
                    type="number"
                    value={formData.maxContextLength}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        maxContextLength: parseInt(e.target.value),
                      })
                    }
                    required
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="temperature">温度 (0-2)</Label>
                  <Input
                    id="temperature"
                    type="number"
                    step="0.1"
                    min="0"
                    max="2"
                    value={formData.temperature}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        temperature: parseFloat(e.target.value),
                      })
                    }
                  />
                </div>
              </div>
              <div className="flex items-center justify-between pt-4">
                <div>
                  {testResult !== null && (
                    <span
                      className={`text-sm font-medium ${
                        testResult ? "text-green-600" : "text-red-600"
                      }`}
                    >
                      {testResult ? "连接成功" : "连接失败"}
                    </span>
                  )}
                </div>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleTest}
                    disabled={
                      isTesting ||
                      !formData.apiUrl ||
                      !formData.apiKey ||
                      !formData.model
                    }
                  >
                    {isTesting ? "测试中..." : "测试连接"}
                  </Button>
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
              <TableHead>模型</TableHead>
              <TableHead>API地址</TableHead>
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
            ) : llms.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={4}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无LLM配置，点击"添加配置"创建
                </TableCell>
              </TableRow>
            ) : (
              llms.map((llm) => (
                <TableRow key={llm.id}>
                  <TableCell className="font-medium">{llm.name}</TableCell>
                  <TableCell>{llm.model}</TableCell>
                  <TableCell className="max-w-xs truncate">
                    {llm.apiUrl}
                  </TableCell>
                  <TableCell className="text-left">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => handleEdit(llm)}
                    >
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => llm.id && handleDelete(llm.id)}
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
              确定要删除这个LLM配置吗？此操作无法撤销。
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
