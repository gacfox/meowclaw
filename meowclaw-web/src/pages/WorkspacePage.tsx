import { Fragment, useCallback, useEffect, useRef, useState } from "react";
import type { AgentDTO, CreateEntryType, FileContent, FileEntry } from "@/types";
import { listAgents } from "@/services/agent";
import {
  createWorkspaceEntry,
  deleteWorkspaceFile,
  listWorkspaceFiles,
  moveWorkspaceFile,
  readWorkspaceFile,
  saveWorkspaceFile,
  uploadWorkspaceFile,
} from "@/services/workspace";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
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
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import {
  ArrowUp,
  FileImage,
  FilePlus,
  FileText,
  Folder,
  FolderPlus,
  Move,
  Pencil,
  RefreshCw,
  Trash2,
  Upload,
} from "lucide-react";
import { toast } from "sonner";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";

const IMAGE_EXTS = new Set(["png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg"]);
const MD_EXTS = new Set(["md", "markdown"]);
const EXT_TO_LANG: Record<string, string> = {
  ts: "typescript", tsx: "typescript", mjs: "javascript", cjs: "javascript", js: "javascript", jsx: "javascript",
  java: "java", kt: "kotlin", py: "python", go: "go", rs: "rust", c: "c", h: "c", cpp: "cpp", cc: "cpp", hpp: "cpp",
  cs: "csharp", rb: "ruby", php: "php", swift: "swift",
  css: "css", scss: "scss", less: "less", html: "html", htm: "html", xml: "xml",
  json: "json", yml: "yaml", yaml: "yaml", toml: "toml", ini: "ini", properties: "properties",
  sh: "bash", bash: "bash", zsh: "bash", bat: "bat", ps1: "powershell",
  sql: "sql", graphql: "graphql", gql: "graphql", vue: "vue", svelte: "svelte",
};

function isMarkdown(name: string): boolean {
  return MD_EXTS.has(extOf(name));
}

function codeFence(name: string, code: string): string {
  const lang = EXT_TO_LANG[extOf(name)] ?? "";
  return (lang ? "```" + lang + "\n" : "```\n") + code + "\n```";
}

function extOf(name: string): string {
  const dot = name.lastIndexOf(".");
  return dot < 0 ? "" : name.slice(dot + 1).toLowerCase();
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function parentOf(path: string): string {
  const segs = path.split("/").filter(Boolean);
  segs.pop();
  return segs.join("/");
}

function segmentsOf(path: string): { name: string; path: string }[] {
  const segs = path.split("/").filter(Boolean);
  return segs.map((seg, i) => ({ name: seg, path: segs.slice(0, i + 1).join("/") }));
}

export function WorkspacePage() {
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [currentPath, setCurrentPath] = useState("");
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchAgents = useCallback(async () => {
    try {
      const list = await listAgents();
      setAgents(list);
      setSelectedAgentId((prev) => prev ?? list[0]?.id ?? null);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载智能体失败");
    }
  }, []);

  const fetchEntries = useCallback(async () => {
    if (selectedAgentId == null) {
      setEntries([]);
      return;
    }
    setLoading(true);
    try {
      setEntries(await listWorkspaceFiles(selectedAgentId, currentPath));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "加载文件列表失败");
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }, [selectedAgentId, currentPath]);

  useEffect(() => {
    fetchAgents();
  }, [fetchAgents]);

  useEffect(() => {
    fetchEntries();
  }, [fetchEntries]);

  const onAgentChange = (id: string) => {
    setSelectedAgentId(Number(id));
    setCurrentPath("");
  };

  const selectedAgent = agents.find((a) => a.id === selectedAgentId) ?? null;

  // ---- 预览 / 编辑 ----
  const [previewEntry, setPreviewEntry] = useState<FileEntry | null>(null);
  const [previewContent, setPreviewContent] = useState<FileContent | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [editText, setEditText] = useState("");
  const [editModified, setEditModified] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [previewMode, setPreviewMode] = useState<"view" | "edit">("view");

  const openPreview = async (entry: FileEntry) => {
    if (selectedAgentId == null) return;
    setPreviewEntry(entry);
    setPreviewContent(null);
    setPreviewError(null);
    setEditText("");
    setEditModified(false);
    setPreviewMode("view");
    setPreviewLoading(true);
    try {
      const c = await readWorkspaceFile(selectedAgentId, entry.path);
      setPreviewContent(c);
      if (c.kind === "TEXT") setEditText(c.content ?? "");
    } catch (err) {
      setPreviewError(err instanceof Error ? err.message : "读取失败");
    } finally {
      setPreviewLoading(false);
    }
  };

  const closePreview = () => {
    setPreviewEntry(null);
    setPreviewContent(null);
    setPreviewError(null);
  };

  const handleSave = async () => {
    if (!previewEntry || selectedAgentId == null) return;
    setEditSaving(true);
    try {
      await saveWorkspaceFile({ agentId: selectedAgentId, path: previewEntry.path, content: editText });
      toast.success("已保存");
      setEditModified(false);
      setPreviewContent((prev) => (prev && prev.kind === "TEXT" ? { ...prev, content: editText } : prev));
      fetchEntries();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "保存失败");
    } finally {
      setEditSaving(false);
    }
  };

  // ---- 移动 / 重命名 ----
  const [moveEntry, setMoveEntry] = useState<FileEntry | null>(null);
  const [moveTo, setMoveTo] = useState("");
  const [moveSaving, setMoveSaving] = useState(false);

  const openMove = (entry: FileEntry) => {
    setMoveEntry(entry);
    setMoveTo(entry.path);
  };

  const handleMove = async () => {
    if (!moveEntry || selectedAgentId == null) return;
    const target = moveTo.trim();
    if (!target || target === moveEntry.path) {
      setMoveEntry(null);
      return;
    }
    setMoveSaving(true);
    try {
      await moveWorkspaceFile({ agentId: selectedAgentId, fromPath: moveEntry.path, toPath: target });
      toast.success("已移动");
      setMoveEntry(null);
      fetchEntries();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "移动失败");
    } finally {
      setMoveSaving(false);
    }
  };

  // ---- 新建 ----
  const [createOpen, setCreateOpen] = useState(false);
  const [createType, setCreateType] = useState<CreateEntryType>("FILE");
  const [createName, setCreateName] = useState("");
  const [createSaving, setCreateSaving] = useState(false);

  const openCreate = (type: CreateEntryType) => {
    setCreateType(type);
    setCreateName("");
    setCreateOpen(true);
  };

  const handleCreate = async () => {
    if (selectedAgentId == null) return;
    const name = createName.trim();
    if (!name) {
      toast.error("请输入名称");
      return;
    }
    const path = currentPath ? `${currentPath}/${name}` : name;
    setCreateSaving(true);
    try {
      await createWorkspaceEntry({ agentId: selectedAgentId, path, type: createType });
      toast.success("已创建");
      setCreateOpen(false);
      fetchEntries();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "创建失败");
    } finally {
      setCreateSaving(false);
    }
  };

  // ---- 删除 ----
  const [deleteEntry, setDeleteEntry] = useState<FileEntry | null>(null);
  const [deleting, setDeleting] = useState(false);

  const handleDelete = async () => {
    if (!deleteEntry || selectedAgentId == null) return;
    setDeleting(true);
    try {
      await deleteWorkspaceFile(selectedAgentId, deleteEntry.path);
      toast.success("已删除");
      setDeleteEntry(null);
      fetchEntries();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "删除失败");
    } finally {
      setDeleting(false);
    }
  };

  // ---- 上传 ----
  const uploadRef = useRef<HTMLInputElement>(null);
  const handleUploadSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || selectedAgentId == null) return;
    try {
      await uploadWorkspaceFile(selectedAgentId, currentPath, file);
      toast.success("上传成功");
      fetchEntries();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "上传失败");
    } finally {
      if (uploadRef.current) uploadRef.current.value = "";
    }
  };

  const segs = segmentsOf(currentPath);
  const atRoot = currentPath === "";

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">工作区</h1>
        <div className="flex items-center gap-2">
          <Select value={selectedAgentId != null ? String(selectedAgentId) : undefined} onValueChange={onAgentChange}>
            <SelectTrigger className="w-[220px]">
              <SelectValue placeholder="选择智能体" />
            </SelectTrigger>
            <SelectContent>
              {agents.map((a) => (
                <SelectItem key={a.id} value={String(a.id)}>
                  {a.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" onClick={fetchEntries} title="刷新" disabled={loading || selectedAgentId == null}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {selectedAgent?.workspaceFolder && (
        <div className="text-xs text-muted-foreground">
          工作区根目录：<code className="break-all">{selectedAgent.workspaceFolder}</code>
        </div>
      )}

      <div className="flex flex-wrap items-center gap-2">
        <Breadcrumb className="min-w-0 flex-1">
          <BreadcrumbList>
            <BreadcrumbItem>
              {atRoot ? (
                <BreadcrumbPage className="flex items-center gap-1">
                  <Folder className="size-3.5" />
                  <span>{selectedAgent?.name ?? "工作区"}</span>
                </BreadcrumbPage>
              ) : (
                <BreadcrumbLink asChild>
                  <button className="flex items-center gap-1" onClick={() => setCurrentPath("")}>
                    <Folder className="size-3.5" />
                    <span>{selectedAgent?.name ?? "工作区"}</span>
                  </button>
                </BreadcrumbLink>
              )}
            </BreadcrumbItem>
            {segs.map((seg, i) => (
              <Fragment key={seg.path}>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  {i === segs.length - 1 ? (
                    <BreadcrumbPage>{seg.name}</BreadcrumbPage>
                  ) : (
                    <BreadcrumbLink asChild>
                      <button onClick={() => setCurrentPath(seg.path)}>{seg.name}</button>
                    </BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>

        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setCurrentPath(parentOf(currentPath))} disabled={atRoot || selectedAgentId == null}>
            <ArrowUp className="mr-1 size-4" />
            上一级
          </Button>
          <Button variant="outline" size="sm" onClick={() => openCreate("DIR")} disabled={selectedAgentId == null}>
            <FolderPlus className="mr-1 size-4" />
            新建文件夹
          </Button>
          <Button variant="outline" size="sm" onClick={() => openCreate("FILE")} disabled={selectedAgentId == null}>
            <FilePlus className="mr-1 size-4" />
            新建文件
          </Button>
          <Button variant="outline" size="sm" onClick={() => uploadRef.current?.click()} disabled={selectedAgentId == null}>
            <Upload className="mr-1 size-4" />
            上传
          </Button>
          <input ref={uploadRef} type="file" className="hidden" onChange={handleUploadSelect} />
        </div>
      </div>

      {selectedAgentId == null ? (
        <div className="py-8 text-center text-muted-foreground">暂无智能体，请先在「智能体」中创建</div>
      ) : loading ? (
        <div className="py-8 text-center text-muted-foreground">加载中...</div>
      ) : entries.length === 0 ? (
        <div className="py-8 text-center text-muted-foreground">空目录</div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>名称</TableHead>
              <TableHead className="w-32">大小</TableHead>
              <TableHead className="w-48">修改时间</TableHead>
              <TableHead className="w-40 text-right">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {entries.map((entry) => (
              <TableRow key={entry.path}>
                <TableCell>
                  <button
                    className="flex items-center gap-2 text-left font-medium hover:underline"
                    onClick={() => (entry.directory ? setCurrentPath(entry.path) : openPreview(entry))}
                  >
                    {entry.directory ? (
                      <Folder className="size-4 text-amber-500" />
                    ) : IMAGE_EXTS.has(extOf(entry.name)) ? (
                      <FileImage className="size-4 text-blue-500" />
                    ) : (
                      <FileText className="size-4 text-muted-foreground" />
                    )}
                    <span>{entry.name}</span>
                  </button>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {entry.directory ? "-" : formatSize(entry.size)}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {entry.lastModified ? new Date(entry.lastModified).toLocaleString() : "-"}
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex items-center justify-end gap-1">
                    {!entry.directory && (
                      <Button variant="ghost" size="icon-sm" onClick={() => openPreview(entry)} title="查看 / 编辑">
                        <Pencil className="size-4" />
                      </Button>
                    )}
                    <Button variant="ghost" size="icon-sm" onClick={() => openMove(entry)} title="移动 / 重命名">
                      <Move className="size-4" />
                    </Button>
                    <Button variant="ghost" size="icon-sm" onClick={() => setDeleteEntry(entry)} title="删除">
                      <Trash2 className="size-4 text-destructive" />
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      {/* 预览 / 编辑 */}
      <Dialog open={!!previewEntry} onOpenChange={(o) => { if (!o) closePreview(); }}>
        <DialogContent className="max-h-[90vh] sm:max-w-6xl overflow-hidden">
          <DialogHeader>
            <DialogTitle className="break-all">{previewEntry?.path}</DialogTitle>
          </DialogHeader>
          {previewLoading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : previewError ? (
            <div className="py-8 text-center text-destructive">{previewError}</div>
          ) : previewContent?.kind === "IMAGE" ? (
            <div className="flex justify-center overflow-y-auto">
              <img src={previewContent.dataUrl ?? undefined} alt={previewEntry?.name} className="max-h-[70vh] rounded" />
            </div>
          ) : previewContent?.kind === "TEXT" ? (
            <div className="flex flex-col gap-3 overflow-hidden">
              <div className="overflow-auto" style={{ maxHeight: "65vh" }}>
                {previewMode === "view" ? (
                  <MarkdownRenderer
                    content={
                      isMarkdown(previewEntry?.name ?? "")
                        ? previewContent.content ?? ""
                        : codeFence(previewEntry?.name ?? "", previewContent.content ?? "")
                    }
                  />
                ) : (
                  <Textarea
                    rows={20}
                    className="resize-y font-mono text-sm"
                    value={editText}
                    onChange={(e) => { setEditText(e.target.value); setEditModified(true); }}
                  />
                )}
              </div>
              <DialogFooter>
                {previewMode === "view" ? (
                  <>
                    <Button variant="outline" onClick={closePreview}>关闭</Button>
                    <Button onClick={() => setPreviewMode("edit")}>编辑</Button>
                  </>
                ) : (
                  <>
                    <Button variant="outline" onClick={() => setPreviewMode("view")}>预览</Button>
                    <Button onClick={handleSave} disabled={editSaving || !editModified}>
                      {editSaving ? "保存中..." : "保存"}
                    </Button>
                  </>
                )}
              </DialogFooter>
            </div>
          ) : (
            <div className="py-8 text-center text-muted-foreground">该文件类型不支持在线预览</div>
          )}
        </DialogContent>
      </Dialog>

      {/* 移动 / 重命名 */}
      <Dialog open={!!moveEntry} onOpenChange={(o) => { if (!o) setMoveEntry(null); }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>移动 / 重命名</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label>目标相对路径</Label>
            <Input value={moveTo} onChange={(e) => setMoveTo(e.target.value)} placeholder="如 notes/draft.md" />
            <p className="text-xs text-muted-foreground">修改文件名即重命名，修改目录前缀即移动。</p>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoveEntry(null)}>取消</Button>
            <Button onClick={handleMove} disabled={moveSaving}>{moveSaving ? "处理中..." : "确定"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 新建 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{createType === "DIR" ? "新建文件夹" : "新建文件"}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label>名称{createType === "DIR" ? "" : "（含扩展名，如 notes.md）"}</Label>
            <Input
              value={createName}
              onChange={(e) => setCreateName(e.target.value)}
              placeholder={createType === "DIR" ? "folder-name" : "notes.md"}
              onKeyDown={(e) => { if (e.key === "Enter") handleCreate(); }}
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button onClick={handleCreate} disabled={createSaving}>{createSaving ? "创建中..." : "创建"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认 */}
      <AlertDialog open={!!deleteEntry} onOpenChange={(o) => { if (!o) setDeleteEntry(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除「{deleteEntry?.name}」吗？{deleteEntry?.directory ? "文件夹及其所有内容将被一并删除，" : ""}此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} disabled={deleting}>
              {deleting ? "删除中..." : "删除"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
