import { useState, useEffect, useCallback, useRef } from "react";
import type { AgentDTO, SkillPackageDTO } from "@/types";
import { listAgents } from "@/services/agent";
import {
  listSkills,
  uploadSkill,
  deleteSkill as apiDeleteSkill,
  installSkill,
} from "@/services/skill";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
import { Plus, Trash2, RefreshCw, PackageCheck, Upload, FileArchive } from "lucide-react";
import { toast } from "sonner";

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

export function SkillPage() {
  const [skills, setSkills] = useState<SkillPackageDTO[]>([]);
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [skillList, agentList] = await Promise.all([listSkills(), listAgents()]);
      setSkills(skillList);
      setAgents(agentList);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  // Upload dialog
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [pendingFile, setPendingFile] = useState<File | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const openUpload = () => {
    setPendingFile(null);
    setUploadOpen(true);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    if (!file.name.toLowerCase().endsWith(".zip")) {
      toast.error("仅支持 .zip 格式");
      return;
    }
    setPendingFile(file);
  };

  const handleUpload = async () => {
    if (!pendingFile) {
      toast.error("请选择 ZIP 文件");
      return;
    }
    setUploading(true);
    try {
      await uploadSkill(pendingFile);
      toast.success("技能包已上传");
      await fetchData();
      setUploadOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "上传失败");
    } finally {
      setUploading(false);
    }
  };

  // Delete dialog
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleting, setDeleting] = useState<SkillPackageDTO | null>(null);

  const openDelete = (skill: SkillPackageDTO) => {
    setDeleting(skill);
    setDeleteOpen(true);
  };

  const handleDelete = async () => {
    if (!deleting) return;
    try {
      await apiDeleteSkill(deleting.id);
      toast.success("已删除（已安装到工作区的副本不受影响）");
      await fetchData();
      setDeleteOpen(false);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    }
  };

  // Install dialog
  const [installOpen, setInstallOpen] = useState(false);
  const [installing, setInstalling] = useState<SkillPackageDTO | null>(null);
  const [selectedAgentId, setSelectedAgentId] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const openInstall = (skill: SkillPackageDTO) => {
    setInstalling(skill);
    setSelectedAgentId("");
    setInstallOpen(true);
  };

  const handleInstall = async (overwrite: boolean) => {
    if (!installing || !selectedAgentId) {
      toast.error("请选择智能体");
      return;
    }
    setSubmitting(true);
    try {
      const result = await installSkill(installing.id, {
        agentId: parseInt(selectedAgentId),
        overwrite,
      });
      if (result.status === "CONFLICT") {
        const ok = window.confirm(
          `目标目录非空，已有：\n${result.existingFiles.slice(0, 10).join("\n")}${result.existingFiles.length > 10 ? `\n... 等 ${result.existingFiles.length} 项` : ""}\n\n是否覆盖安装？`,
        );
        if (!ok) {
          setSubmitting(false);
          return;
        }
        const overwriteResult = await installSkill(installing.id, {
          agentId: parseInt(selectedAgentId),
          overwrite: true,
        });
        if (overwriteResult.status === "INSTALLED") {
          toast.success("已覆盖安装");
          setInstallOpen(false);
        }
      } else {
        const agentName = agents.find((a) => a.id === parseInt(selectedAgentId))?.name ?? "";
        toast.success(`已安装到「${agentName}」`);
        setInstallOpen(false);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "安装失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">SKILL 技能包</h1>
        <div className="flex items-center gap-2">
          <Button onClick={openUpload}>
            <Plus className="mr-1 size-4" />
            创建 SKILL
          </Button>
          <Button variant="outline" size="icon" onClick={fetchData} title="刷新" disabled={loading}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : skills.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">暂无技能包，点击「创建 SKILL」上传</div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {skills.map((skill) => (
            <Card key={skill.id}>
              <CardHeader className="flex flex-row items-start justify-between space-y-0 pb-2">
                <div className="flex items-center gap-2">
                  <FileArchive className="size-4 text-muted-foreground" />
                  <CardTitle className="text-base">{skill.name}</CardTitle>
                </div>
                <div className="flex gap-1">
                  <Button variant="ghost" size="icon-sm" onClick={() => openInstall(skill)} title="安装到智能体">
                    <PackageCheck className="size-4" />
                  </Button>
                  <Button variant="ghost" size="icon-sm" onClick={() => openDelete(skill)} title="删除">
                    <Trash2 className="size-4 text-destructive" />
                  </Button>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col gap-2 text-sm">
                {skill.description && (
                  <p className="line-clamp-3 text-muted-foreground">{skill.description}</p>
                )}
                <div className="text-xs text-muted-foreground">
                  <div className="truncate" title={skill.originalFilename}>
                    {skill.originalFilename}
                  </div>
                  <div>
                    {formatSize(skill.fileSize)}
                  </div>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Upload Dialog */}
      <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>创建 SKILL</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <p className="text-sm text-muted-foreground">
              上传 ZIP 压缩包，根目录必须包含 <code>SKILL.md</code>，frontmatter 至少声明 <code>name</code>。
            </p>
            <div className="flex items-center gap-2">
              <Button variant="outline" type="button" onClick={() => fileInputRef.current?.click()}>
                <Upload className="mr-1 size-4" />
                选择 ZIP 文件
              </Button>
              <input
                ref={fileInputRef}
                type="file"
                accept=".zip"
                className="hidden"
                onChange={handleFileChange}
              />
              {pendingFile && (
                <span className="truncate text-sm text-muted-foreground" title={pendingFile.name}>
                  {pendingFile.name} ({formatSize(pendingFile.size)})
                </span>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadOpen(false)}>取消</Button>
            <Button onClick={handleUpload} disabled={uploading || !pendingFile}>
              {uploading ? "上传中..." : "上传"}
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
              确定要删除技能包「{deleting?.name}」吗？已安装到智能体工作区的副本不会被影响。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Install Dialog */}
      <Dialog open={installOpen} onOpenChange={setInstallOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>安装技能 — {installing?.name}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3">
            <p className="text-sm text-muted-foreground">
              选择要安装此技能的智能体，将解压到工作区 <code>.skill/{installing?.name}/</code>。
            </p>
            <Select value={selectedAgentId} onValueChange={setSelectedAgentId}>
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
          <DialogFooter>
            <Button variant="outline" onClick={() => setInstallOpen(false)}>取消</Button>
            <Button onClick={() => handleInstall(false)} disabled={submitting || !selectedAgentId}>
              {submitting ? "安装中..." : "确认安装"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
