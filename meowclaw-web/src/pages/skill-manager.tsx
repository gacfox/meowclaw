import React, { useEffect, useState } from "react";
import {
  Upload,
  Trash2,
  Plus,
  Pencil,
  Sparkles,
  Download,
  Eye,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Spinner } from "@/components/ui/spinner";
import {
  Card,
  CardContent,
  CardDescription,
  CardTitle,
} from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { ScrollArea } from "@/components/ui/scroll-area";
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
import { skillService, type SkillDto } from "@/services/skill";
import { toast } from "sonner";

const SKILL_NAME_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

export const SkillManager: React.FC = () => {
  const [skills, setSkills] = useState<SkillDto[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isUploading, setIsUploading] = useState(false);
  const [isUploadDialogOpen, setIsUploadDialogOpen] = useState(false);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingSkillId, setDeletingSkillId] = useState<number | null>(null);
  const [isEditDialogOpen, setIsEditDialogOpen] = useState(false);
  const [editingSkill, setEditingSkill] = useState<SkillDto | null>(null);
  const [editingName, setEditingName] = useState("");
  const [editingDescription, setEditingDescription] = useState("");
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewContent, setPreviewContent] = useState("");
  const [isPreviewLoading, setIsPreviewLoading] = useState(false);

  useEffect(() => {
    loadSkills();
  }, []);

  const loadSkills = async () => {
    setIsLoading(true);
    try {
      const response = await skillService.list();
      if (response.code === 200 && response.data) {
        setSkills(response.data);
      }
    } catch (error) {
      console.error("加载技能失败", error);
    } finally {
      setIsLoading(false);
    }
  };

  const resetForm = () => {
    setName("");
    setDescription("");
    setFile(null);
  };

  const openEditDialog = (skill: SkillDto) => {
    setEditingSkill(skill);
    setEditingName(skill.name);
    setEditingDescription(skill.description || "");
    setIsEditDialogOpen(true);
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      alert("技能名称不能为空");
      return;
    }
    if (!SKILL_NAME_PATTERN.test(name.trim())) {
      alert("技能名称必须是 kebab-case");
      return;
    }
    if (!file) {
      alert("请选择技能包 zip 文件");
      return;
    }

    setIsUploading(true);
    try {
      const response = await skillService.upload({
        name: name.trim(),
        description: description.trim() || undefined,
        file,
      });
      if (response.code === 200) {
        resetForm();
        setIsUploadDialogOpen(false);
        loadSkills();
      }
    } catch (error) {
      console.error("上传技能失败", error);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDelete = (id: number) => {
    setDeletingSkillId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingSkillId) return;
    try {
      const response = await skillService.delete(deletingSkillId);
      if (response.code === 200) {
        loadSkills();
      } else {
        toast.error(response.message || "删除技能失败");
      }
    } catch (error) {
      console.error("删除技能失败", error);
      toast.error("删除技能失败，请稍后重试");
    } finally {
      setDeleteDialogOpen(false);
      setDeletingSkillId(null);
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingSkill?.id) return;
    if (!editingName.trim()) {
      toast.error("技能名称不能为空");
      return;
    }
    if (!SKILL_NAME_PATTERN.test(editingName.trim())) {
      toast.error("技能名称必须是 kebab-case");
      return;
    }
    try {
      const response = await skillService.update(editingSkill.id, {
        name: editingName.trim(),
        description: editingDescription.trim() || undefined,
      });
      if (response.code === 200) {
        setIsEditDialogOpen(false);
        setEditingSkill(null);
        loadSkills();
      }
    } catch (error) {
      console.error("更新技能失败", error);
      toast.error("更新技能失败，请稍后重试");
    }
  };

  const handleDownload = async (skill: SkillDto) => {
    if (!skill.id) return;
    try {
      await skillService.download(skill.id, `${skill.name}.zip`);
    } catch (error) {
      console.error("下载技能失败", error);
      toast.error("下载失败，请稍后重试");
    }
  };

  const handlePreview = async (skill: SkillDto) => {
    if (!skill.id) return;
    setIsPreviewOpen(true);
    setPreviewContent("");
    setIsPreviewLoading(true);
    try {
      const response = await skillService.preview(skill.id);
      if (response.code === 200 && response.data !== undefined) {
        setPreviewContent(response.data || "");
      } else {
        toast.error(response.message || "预览失败");
      }
    } catch (error) {
      console.error("预览技能失败", error);
      toast.error("预览失败，请稍后重试");
    } finally {
      setIsPreviewLoading(false);
    }
  };

  return (
    <div className="p-6 h-full flex flex-col min-h-0">
      <div className="mb-6 flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">技能</h1>
          <p className="text-sm text-muted-foreground mt-1">上传并管理技能包</p>
        </div>
        <Dialog open={isUploadDialogOpen} onOpenChange={setIsUploadDialogOpen}>
          <DialogTrigger asChild>
            <Button>
              <Plus className="h-4 w-4 mr-2" />
              导入技能
            </Button>
          </DialogTrigger>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>上传技能包</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleUpload} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="skill-name">技能名称</Label>
                <Input
                  id="skill-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="my-custom-skill"
                  required
                />
                <p className="text-xs text-muted-foreground">
                  仅允许 kebab-case，例如 my-custom-skill
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="skill-desc">描述（可选）</Label>
                <Textarea
                  id="skill-desc"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="简要描述技能用途"
                  rows={3}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="skill-file">zip技能包</Label>
                <div className="flex items-center gap-2">
                  <label
                    htmlFor="skill-file"
                    className="inline-flex items-center gap-2 px-3 py-2 border rounded-md cursor-pointer hover:bg-muted transition-colors"
                  >
                    <Upload className="h-4 w-4" />
                    <span>选择文件</span>
                  </label>
                  <Input
                    id="skill-file"
                    type="file"
                    accept=".zip"
                    onChange={(e) => setFile(e.target.files?.[0] || null)}
                    className="hidden"
                    required
                  />
                  {file && (
                    <span className="text-sm text-muted-foreground">
                      {file.name}
                    </span>
                  )}
                </div>
                <p className="text-xs text-muted-foreground">
                  zip 根目录必须包含 SKILL.md
                </p>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setIsUploadDialogOpen(false)}
                  disabled={isUploading}
                >
                  取消
                </Button>
                <Button type="submit" disabled={isUploading}>
                  {isUploading ? "导入中..." : "导入"}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <div className="flex-1 overflow-auto">
        {isLoading ? (
          <div className="flex items-center justify-center py-12">
            <Spinner className="size-6" />
          </div>
        ) : skills.length === 0 ? (
          <div className="text-center text-muted-foreground py-12">
            暂无技能，请先上传
          </div>
        ) : (
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3 p-1">
            {skills.map((skill) => (
              <Card key={skill.id || skill.name}>
                <CardContent className="space-y-3">
                  <div className="flex items-start justify-between gap-3">
                    <CardTitle className="flex items-center gap-2 text-base">
                      <Sparkles className="h-4 w-4" />
                      {skill.name}
                    </CardTitle>
                    <div className="flex items-center gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDownload(skill)}
                        title="下载"
                      >
                        <Download className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handlePreview(skill)}
                        title="预览"
                      >
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditDialog(skill)}
                        title="编辑"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      {skill.id && (
                        <Button
                          variant="ghost"
                          size="icon"
                          className="text-destructive hover:text-destructive"
                          onClick={() => handleDelete(skill.id!)}
                          title="删除"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      )}
                    </div>
                  </div>
                  <CardDescription className="line-clamp-3">
                    {skill.description || "暂无描述"}
                  </CardDescription>
                  <div className="text-xs text-muted-foreground">
                    {skill.createdAt
                      ? `创建时间：${new Date(skill.createdAt).toLocaleString("zh-CN")}`
                      : ""}
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}
      </div>

      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              删除技能将同时移除技能包文件，且正在被智能体使用的技能无法删除。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={isEditDialogOpen} onOpenChange={setIsEditDialogOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>编辑技能</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleUpdate} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="edit-skill-name">技能名称</Label>
              <Input
                id="edit-skill-name"
                value={editingName}
                onChange={(e) => setEditingName(e.target.value)}
                disabled
              />
              <p className="text-xs text-muted-foreground">
                技能名称不可修改，如需修改请重新上传
              </p>
            </div>
            <div className="space-y-2">
              <Label htmlFor="edit-skill-desc">描述（可选）</Label>
              <Textarea
                id="edit-skill-desc"
                value={editingDescription}
                onChange={(e) => setEditingDescription(e.target.value)}
                rows={3}
              />
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsEditDialogOpen(false)}
              >
                取消
              </Button>
              <Button type="submit">保存</Button>
            </div>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={isPreviewOpen} onOpenChange={setIsPreviewOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh]">
          <DialogHeader>
            <DialogTitle>SKILL.md</DialogTitle>
          </DialogHeader>
          <ScrollArea className="h-[60vh]">
            {isPreviewLoading ? (
              <div className="flex items-center justify-center py-8">
                <Spinner className="size-6" />
              </div>
            ) : previewContent ? (
              <pre className="whitespace-pre-wrap text-sm leading-6">
                {previewContent}
              </pre>
            ) : (
              <div className="text-sm text-muted-foreground py-4">
                无可预览内容
              </div>
            )}
          </ScrollArea>
        </DialogContent>
      </Dialog>
    </div>
  );
};
