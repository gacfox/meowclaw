import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import type {
  AgentDTO,
  ConflictStrategy,
  CreateEntryType,
  FileContent,
  FileEntry,
  FileOperationResult,
  WorkspaceProperties,
} from "@/types";
import { listAgents } from "@/services/agent";
import { readSelectedAgentId, writeSelectedAgentId } from "@/lib/selectedAgent";
import {
  copyWorkspaceEntries,
  createWorkspaceEntry,
  deleteWorkspaceEntries,
  getWorkspaceProperties,
  listWorkspaceFiles,
  moveWorkspaceEntries,
  prepareWorkspaceDownload,
  readWorkspaceFile,
  renameWorkspaceEntry,
  saveWorkspaceFile,
} from "@/services/workspace";
import { useWorkspaceUpload } from "@/context/WorkspaceUploadContext";
import { useWorkspaceSelection } from "@/hooks/useWorkspaceSelection";
import { useTouchInteraction } from "@/hooks/useTouchInteraction";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  ContextMenu,
  ContextMenuContent,
  ContextMenuItem,
  ContextMenuLabel,
  ContextMenuSeparator,
  ContextMenuTrigger,
} from "@/components/ui/context-menu";
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
  Check,
  ClipboardPaste,
  Copy,
  Download,
  Eye,
  FilePlus,
  FolderPlus,
  Grid2X2,
  Info,
  LayoutGrid,
  List,
  ListChecks,
  Loader2,
  MoreVertical,
  Pencil,
  RefreshCw,
  Scissors,
  Trash2,
  Upload,
} from "lucide-react";
import { toast } from "sonner";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { WorkspaceEntryView, type WorkspaceViewMode } from "@/components/workspace/WorkspaceEntryView";
import { extensionOf, fileTypeLabel, formatFileSize } from "@/lib/workspaceFiles";
import { WorkspaceConflictDialog } from "@/components/workspace/WorkspaceConflictDialog";
import { WorkspaceTouchActionSheet } from "@/components/workspace/WorkspaceTouchActionSheet";

const MD_EXTS = new Set(["md", "markdown"]);
const EXT_TO_LANG: Record<string, string> = {
  ts: "typescript", tsx: "typescript", mjs: "javascript", cjs: "javascript", js: "javascript", jsx: "javascript",
  java: "java", kt: "kotlin", py: "python", go: "go", rs: "rust", c: "c", h: "c", cpp: "cpp", cc: "cpp", hpp: "hpp",
  cs: "csharp", rb: "ruby", php: "php", swift: "swift",
  css: "css", scss: "scss", less: "less", html: "html", htm: "html", xml: "xml",
  json: "json", yml: "yaml", yaml: "yaml", toml: "toml", ini: "ini", properties: "properties",
  sh: "bash", bash: "bash", zsh: "bash", bat: "bat", ps1: "powershell",
  sql: "sql", graphql: "graphql", gql: "graphql", vue: "vue", svelte: "svelte",
};
const VIEW_MODE_KEY = "meowclaw_workspace_view_mode";

function codeFence(name: string, code: string): string {
  const lang = EXT_TO_LANG[extensionOf(name)] ?? "";
  return (lang ? "```" + lang + "\n" : "```\n") + code + "\n```";
}

function parentOf(path: string): string {
  const segments = path.split("/").filter(Boolean);
  segments.pop();
  return segments.join("/");
}

function normalizeRelativePath(path: string | null): string {
  return (path ?? "").replace(/^\/+|\/+$/g, "");
}

function segmentsOf(path: string): { name: string; path: string }[] {
  const segments = path.split("/").filter(Boolean);
  return segments.map((name, index) => ({ name, path: segments.slice(0, index + 1).join("/") }));
}

function readViewMode(): WorkspaceViewMode {
  try {
    const value = localStorage.getItem(VIEW_MODE_KEY);
    if (value === "large" || value === "medium" || value === "details") return value;
  } catch { /* 存储不可用时使用默认视图 */ }
  return "details";
}

function isConflictMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return normalized.includes("已存在") || normalized.includes("同名") || normalized.includes("冲突")
    || normalized.includes("exist") || normalized.includes("conflict");
}

function failedResults(result: FileOperationResult) {
  return result.results.filter((item) => !item.success && !item.skipped);
}

interface WorkspaceClipboard {
  agentId: number;
  mode: "copy" | "cut";
  entries: FileEntry[];
}

interface ConflictChoice {
  strategy: ConflictStrategy;
  applyAll: boolean;
}

export function WorkspacePage() {
  const [searchParams] = useSearchParams();
  const { enqueue } = useWorkspaceUpload();
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [currentPath, setCurrentPath] = useState(() => normalizeRelativePath(searchParams.get("path")));
  const [entries, setEntries] = useState<FileEntry[]>([]);
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewModeState] = useState<WorkspaceViewMode>(readViewMode);
  const [operationPending, setOperationPending] = useState(false);
  const [contextEntry, setContextEntry] = useState<FileEntry | null>(null);
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const [desktopMenuOpen, setDesktopMenuOpen] = useState(false);
  const operationLockRef = useRef(false);
  const entriesRequestRef = useRef(0);
  const previewRequestRef = useRef(0);
  const saveRequestRef = useRef(0);
  const propertiesRequestRef = useRef(0);
  const locationRef = useRef({ agentId: selectedAgentId, path: currentPath });
  useEffect(() => {
    locationRef.current = { agentId: selectedAgentId, path: currentPath };
  }, [selectedAgentId, currentPath]);
  const {
    selectedPaths,
    selectionMode,
    clearSelection,
    selectSingle,
    selectAll,
    enterSelectionMode,
    exitSelectionMode,
    toggleSelection,
    pruneSelection,
    handleEntryClick,
  } = useWorkspaceSelection(entries);

  const handleInteractionModeChange = useCallback(() => {
    setMobileSheetOpen(false);
    setDesktopMenuOpen(false);
    setContextEntry(null);
    clearSelection();
  }, [clearSelection]);
  const touchMode = useTouchInteraction(handleInteractionModeChange);

  const selectedEntries = useMemo(
    () => entries.filter((entry) => selectedPaths.has(entry.path)),
    [entries, selectedPaths],
  );
  const selectedAgent = agents.find((agent) => agent.id === selectedAgentId) ?? null;

  const fetchAgents = useCallback(async () => {
    try {
      const list = await listAgents();
      setAgents(list);
      const urlAgentId = Number(searchParams.get("agentId"));
      const cachedAgentId = readSelectedAgentId();
      const validAgentId = list.some((agent) => agent.id === urlAgentId)
        ? urlAgentId
        : cachedAgentId != null && list.some((agent) => agent.id === cachedAgentId)
          ? cachedAgentId
          : list[0]?.id ?? null;
      setSelectedAgentId(validAgentId);
      if (validAgentId != null) writeSelectedAgentId(validAgentId);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "加载智能体失败");
    }
  }, [searchParams]);

  const fetchEntries = useCallback(async () => {
    if (locationRef.current.agentId !== selectedAgentId || locationRef.current.path !== currentPath) return;
    const requestId = ++entriesRequestRef.current;
    const isCurrentRequest = () => requestId === entriesRequestRef.current
      && locationRef.current.agentId === selectedAgentId && locationRef.current.path === currentPath;
    if (selectedAgentId == null) {
      setEntries([]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const nextEntries = await listWorkspaceFiles(selectedAgentId, currentPath);
      if (!isCurrentRequest()) return;
      setEntries(nextEntries);
      pruneSelection(nextEntries);
    } catch (error) {
      if (!isCurrentRequest()) return;
      toast.error(error instanceof Error ? error.message : "加载文件列表失败");
      setEntries([]);
      clearSelection();
    } finally {
      if (isCurrentRequest()) setLoading(false);
    }
  }, [selectedAgentId, currentPath, pruneSelection, clearSelection]);

  useEffect(() => { fetchAgents(); }, [fetchAgents]);
  useEffect(() => { fetchEntries(); }, [fetchEntries]);

  useEffect(() => {
    const handleUploadCompleted = (event: Event) => {
      const detail = (event as CustomEvent<{ agentId: number; targetDir: string }>).detail;
      if (detail?.agentId === selectedAgentId && detail.targetDir === currentPath) fetchEntries();
    };
    window.addEventListener("workspace-upload-completed", handleUploadCompleted);
    return () => window.removeEventListener("workspace-upload-completed", handleUploadCompleted);
  }, [selectedAgentId, currentPath, fetchEntries]);

  const navigateTo = useCallback((path: string) => {
    const nextPath = normalizeRelativePath(path);
    entriesRequestRef.current += 1;
    locationRef.current = { agentId: selectedAgentId, path: nextPath };
    setMobileSheetOpen(false);
    setDesktopMenuOpen(false);
    setContextEntry(null);
    setCurrentPath(nextPath);
    setEntries([]);
    setLoading(true);
    clearSelection();
  }, [clearSelection, selectedAgentId]);

  const onAgentChange = (id: string) => {
    const agentId = Number(id);
    setSelectedAgentId(agentId);
    writeSelectedAgentId(agentId);
    navigateTo("");
    locationRef.current = { agentId, path: "" };
  };

  const setViewMode = (mode: WorkspaceViewMode) => {
    setViewModeState(mode);
    try { localStorage.setItem(VIEW_MODE_KEY, mode); } catch { /* 仅保持当前页面状态 */ }
  };

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
    const requestId = ++previewRequestRef.current;
    saveRequestRef.current += 1;
    setPreviewEntry(entry);
    setPreviewContent(null);
    setPreviewError(null);
    setEditText("");
    setEditModified(false);
    setEditSaving(false);
    setPreviewMode("view");
    setPreviewLoading(true);
    try {
      const content = await readWorkspaceFile(selectedAgentId, entry.path);
      if (requestId !== previewRequestRef.current) return;
      setPreviewContent(content);
      if (content.kind === "TEXT") setEditText(content.content ?? "");
    } catch (error) {
      if (requestId !== previewRequestRef.current) return;
      setPreviewError(error instanceof Error ? error.message : "读取失败");
    } finally {
      if (requestId === previewRequestRef.current) setPreviewLoading(false);
    }
  };

  const openEntry = (entry: FileEntry) => {
    if (touchMode) clearSelection();
    if (entry.directory) navigateTo(entry.path);
    else openPreview(entry);
  };

  const closePreview = () => {
    previewRequestRef.current += 1;
    saveRequestRef.current += 1;
    setPreviewEntry(null);
    setPreviewContent(null);
    setPreviewError(null);
    setEditSaving(false);
  };

  const handleSave = async () => {
    if (!previewEntry || selectedAgentId == null || editSaving) return;
    const agentId = selectedAgentId;
    const path = previewEntry.path;
    const content = editText;
    const previewRequestId = previewRequestRef.current;
    const saveRequestId = ++saveRequestRef.current;
    setEditSaving(true);
    try {
      await saveWorkspaceFile({ agentId, path, content });
      if (saveRequestId !== saveRequestRef.current || previewRequestId !== previewRequestRef.current) return;
      toast.success("已保存");
      setEditModified(false);
      setPreviewContent((previous) => previous?.kind === "TEXT" ? { ...previous, content } : previous);
      fetchEntries();
    } catch (error) {
      if (saveRequestId === saveRequestRef.current && previewRequestId === previewRequestRef.current) {
        toast.error(error instanceof Error ? error.message : "保存失败");
      }
    } finally {
      if (saveRequestId === saveRequestRef.current) setEditSaving(false);
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
    if (selectedAgentId == null || createSaving) return;
    const name = createName.trim();
    if (!name) {
      toast.error("请输入名称");
      return;
    }
    setCreateSaving(true);
    try {
      await createWorkspaceEntry({
        agentId: selectedAgentId,
        path: currentPath ? `${currentPath}/${name}` : name,
        type: createType,
      });
      toast.success("已创建");
      setCreateOpen(false);
      fetchEntries();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "创建失败");
    } finally {
      setCreateSaving(false);
    }
  };

  // ---- 复制 / 剪切 / 粘贴 ----
  const [clipboard, setClipboard] = useState<WorkspaceClipboard | null>(null);
  const [conflictName, setConflictName] = useState<string | null>(null);
  const [conflictAllowApplyAll, setConflictAllowApplyAll] = useState(true);
  const conflictResolverRef = useRef<((choice: ConflictChoice) => void) | null>(null);

  const canPaste = clipboard != null && clipboard.agentId === selectedAgentId;
  const cutPaths = useMemo(
    () => new Set(clipboard?.mode === "cut" && clipboard.agentId === selectedAgentId
      ? clipboard.entries.map((entry) => entry.path)
      : []),
    [clipboard, selectedAgentId],
  );
  const contextTargets = contextEntry
    ? selectedPaths.has(contextEntry.path) ? selectedEntries : [contextEntry]
    : [];

  const askConflict = useCallback((name: string, allowApplyAll = true) => {
    setConflictAllowApplyAll(allowApplyAll);
    setConflictName(name);
    return new Promise<ConflictChoice>((resolve) => {
      conflictResolverRef.current = resolve;
    });
  }, []);

  const chooseConflict = (strategy: ConflictStrategy, applyAll: boolean) => {
    setConflictName(null);
    conflictResolverRef.current?.({ strategy, applyAll });
    conflictResolverRef.current = null;
  };

  const setClipboardFor = (mode: "copy" | "cut", targets: FileEntry[]) => {
    if (selectedAgentId == null || targets.length === 0) return;
    setClipboard({ agentId: selectedAgentId, mode, entries: targets });
    toast.success(`${mode === "copy" ? "已复制" : "已剪切"} ${targets.length} 个项目`);
  };

  const handlePaste = async (targetDir: string) => {
    if (!clipboard || selectedAgentId == null || clipboard.agentId !== selectedAgentId || operationLockRef.current) return;
    const operationClipboard = clipboard;
    operationLockRef.current = true;
    setOperationPending(true);
    try {
      let rememberedStrategy: ConflictStrategy | null = null;
      const failedPaths = new Set<string>();
      let completed = 0;

      for (const entry of operationClipboard.entries) {
        let strategy = rememberedStrategy ?? "FAIL";
        let handled = false;
        while (!handled) {
          try {
            const operation = operationClipboard.mode === "copy" ? copyWorkspaceEntries : moveWorkspaceEntries;
            const result = await operation({
              agentId: selectedAgentId,
              sourcePaths: [entry.path],
              targetDir,
              conflict: strategy,
            });
            const item = result.results[0];
            if (item?.success || item?.skipped) {
              completed += 1;
              handled = true;
              break;
            }
            const message = item?.message || "操作失败";
            if (!isConflictMessage(message)) throw new Error(message);
          } catch (error) {
            const message = error instanceof Error ? error.message : "操作失败";
            if (!isConflictMessage(message)) {
              toast.error(`处理「${entry.name}」失败：${message}`);
              failedPaths.add(entry.path);
              break;
            }
          }

          if (rememberedStrategy && strategy !== rememberedStrategy) {
            strategy = rememberedStrategy;
            continue;
          }
          const choice = await askConflict(entry.name);
          if (choice.applyAll) rememberedStrategy = choice.strategy;
          strategy = choice.strategy;
        }
      }

      if (operationClipboard.mode === "cut") {
        setClipboard((previous) => {
          if (previous !== operationClipboard) return previous;
          const remaining = previous.entries.filter((entry) => failedPaths.has(entry.path));
          return remaining.length > 0 ? { ...previous, entries: remaining } : null;
        });
      }
      if (completed > 0) toast.success(`已处理 ${completed} 个项目`);
      fetchEntries();
    } finally {
      operationLockRef.current = false;
      setOperationPending(false);
    }
  };

  // ---- 重命名 ----
  const [renameEntry, setRenameEntry] = useState<FileEntry | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [renaming, setRenaming] = useState(false);

  const openRename = (entry: FileEntry) => {
    if (operationLockRef.current) return;
    setRenameEntry(entry);
    setRenameValue(entry.name);
  };

  const handleRename = async () => {
    if (!renameEntry || selectedAgentId == null || !renameValue.trim() || operationLockRef.current) return;
    operationLockRef.current = true;
    setOperationPending(true);
    setRenaming(true);
    let strategy: ConflictStrategy = "FAIL";
    try {
      while (true) {
        try {
          const result = await renameWorkspaceEntry({
            agentId: selectedAgentId,
            path: renameEntry.path,
            newName: renameValue.trim(),
            conflict: strategy,
          });
          const item = result.results[0];
          if (item?.success || item?.skipped) {
            toast.success(item.skipped ? "已跳过重命名" : "已重命名");
            setRenameEntry(null);
            fetchEntries();
            break;
          }
          const message = item?.message || "重命名失败";
          if (!isConflictMessage(message)) throw new Error(message);
        } catch (error) {
          const message = error instanceof Error ? error.message : "重命名失败";
          if (!isConflictMessage(message)) throw error;
        }
        const choice = await askConflict(renameValue.trim(), false);
        strategy = choice.strategy;
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "重命名失败");
    } finally {
      operationLockRef.current = false;
      setOperationPending(false);
      setRenaming(false);
    }
  };

  // ---- 删除 ----
  const [deleteEntries, setDeleteEntries] = useState<FileEntry[]>([]);
  const [deleting, setDeleting] = useState(false);

  const handleDelete = async () => {
    if (selectedAgentId == null || deleteEntries.length === 0 || operationLockRef.current) return;
    operationLockRef.current = true;
    setOperationPending(true);
    setDeleting(true);
    try {
      const result = await deleteWorkspaceEntries(selectedAgentId, deleteEntries.map((entry) => entry.path));
      const failed = failedResults(result);
      if (failed.length > 0) {
        toast.error(`${failed.length} 个项目删除失败：${failed[0].message ?? "未知错误"}`);
      } else {
        toast.success(`已删除 ${deleteEntries.length} 个项目`);
      }
      setDeleteEntries([]);
      clearSelection();
      fetchEntries();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "删除失败");
    } finally {
      operationLockRef.current = false;
      setOperationPending(false);
      setDeleting(false);
    }
  };

  // ---- 下载 ----
  const [downloading, setDownloading] = useState(false);

  const handleDownload = async (targets: FileEntry[]) => {
    if (selectedAgentId == null || targets.length === 0 || downloading) return;
    setDownloading(true);
    try {
      const prepared = await prepareWorkspaceDownload(selectedAgentId, targets.map((entry) => entry.path));
      const link = document.createElement("a");
      link.href = `/api/workspace/download-ticket/${encodeURIComponent(prepared.ticket)}`;
      link.download = prepared.filename;
      link.style.display = "none";
      document.body.appendChild(link);
      link.click();
      link.remove();
      toast.success(targets.length === 1 && !targets[0].directory ? "下载已开始" : "ZIP 下载已开始");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "下载失败");
    } finally {
      setDownloading(false);
    }
  };

  // ---- 属性 ----
  const [propertiesEntries, setPropertiesEntries] = useState<FileEntry[]>([]);
  const [properties, setProperties] = useState<WorkspaceProperties | null>(null);
  const [propertiesLoading, setPropertiesLoading] = useState(false);

  const openProperties = async (targets: FileEntry[]) => {
    if (selectedAgentId == null || targets.length === 0) return;
    const requestId = ++propertiesRequestRef.current;
    setPropertiesEntries(targets);
    setProperties(null);
    setPropertiesLoading(true);
    try {
      const result = await getWorkspaceProperties(selectedAgentId, targets.map((entry) => entry.path));
      if (requestId === propertiesRequestRef.current) setProperties(result);
    } catch (error) {
      if (requestId !== propertiesRequestRef.current) return;
      toast.error(error instanceof Error ? error.message : "读取属性失败");
      setPropertiesEntries([]);
    } finally {
      if (requestId === propertiesRequestRef.current) setPropertiesLoading(false);
    }
  };

  const closeProperties = () => {
    propertiesRequestRef.current += 1;
    setPropertiesEntries([]);
    setPropertiesLoading(false);
  };

  // ---- 上传 ----
  const uploadRef = useRef<HTMLInputElement>(null);
  const handleUploadSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (files?.length && selectedAgentId != null) enqueue(files, selectedAgentId, currentPath);
    event.target.value = "";
  };

  const handleContextEntry = (entry: FileEntry) => {
    if (!selectedPaths.has(entry.path)) selectSingle(entry.path);
    setContextEntry(entry);
  };

  const handleLongPressEntry = (entry: FileEntry) => {
    if (!touchMode || selectionMode) return;
    handleContextEntry(entry);
    setMobileSheetOpen(true);
  };

  const openMobileDirectoryMenu = () => {
    setContextEntry(null);
    setMobileSheetOpen(true);
  };

  const handleMobileSelect = () => {
    if (contextEntry && !selectedPaths.has(contextEntry.path)) enterSelectionMode(contextEntry.path);
    else enterSelectionMode();
  };

  const handleListKeyDown = (event: React.KeyboardEvent) => {
    if (selectedAgentId == null || operationPending) return;
    const modifier = event.ctrlKey || event.metaKey;
    if (modifier && event.key.toLowerCase() === "a") {
      event.preventDefault();
      selectAll();
    } else if (modifier && event.key.toLowerCase() === "c" && selectedEntries.length > 0) {
      event.preventDefault();
      setClipboardFor("copy", selectedEntries);
    } else if (modifier && event.key.toLowerCase() === "x" && selectedEntries.length > 0) {
      event.preventDefault();
      setClipboardFor("cut", selectedEntries);
    } else if (modifier && event.key.toLowerCase() === "v" && canPaste) {
      event.preventDefault();
      handlePaste(currentPath);
    } else if (event.key === "Delete" && selectedEntries.length > 0) {
      event.preventDefault();
      setDeleteEntries(selectedEntries);
    } else if (event.key === "F2" && selectedEntries.length === 1) {
      event.preventDefault();
      openRename(selectedEntries[0]);
    }
  };

  const segments = segmentsOf(currentPath);
  const atRoot = currentPath === "";
  const noAgent = selectedAgentId == null;

  return (
    <div className="flex h-[calc(100dvh-7.5rem)] min-h-[30rem] flex-col gap-4">
      <div className="flex shrink-0 items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">工作区</h1>
        <div className="flex items-center gap-2">
          <Select value={selectedAgentId != null ? String(selectedAgentId) : undefined} onValueChange={onAgentChange}>
            <SelectTrigger className="w-[220px]">
              <SelectValue placeholder="选择智能体" />
            </SelectTrigger>
            <SelectContent>
              {agents.map((agent) => <SelectItem key={agent.id} value={String(agent.id)}>{agent.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Button variant="outline" size="icon" onClick={fetchEntries} title="刷新" disabled={loading || noAgent}>
            <RefreshCw className={loading ? "size-4 animate-spin" : "size-4"} />
          </Button>
        </div>
      </div>

      {selectedAgent?.workspaceFolder && (
        <div className="shrink-0 text-xs text-muted-foreground">
          工作区根目录：<code className="break-all">{selectedAgent.workspaceFolder}</code>
        </div>
      )}

      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <Breadcrumb className="min-w-48 flex-1">
          <BreadcrumbList>
            <BreadcrumbItem>
              {atRoot ? (
                <BreadcrumbPage>{selectedAgent?.name ?? "工作区"}</BreadcrumbPage>
              ) : (
                <BreadcrumbLink asChild>
                  <button onClick={() => navigateTo("")}>{selectedAgent?.name ?? "工作区"}</button>
                </BreadcrumbLink>
              )}
            </BreadcrumbItem>
            {segments.map((segment, index) => (
              <Fragment key={segment.path}>
                <BreadcrumbSeparator />
                <BreadcrumbItem>
                  {index === segments.length - 1 ? (
                    <BreadcrumbPage>{segment.name}</BreadcrumbPage>
                  ) : (
                    <BreadcrumbLink asChild><button onClick={() => navigateTo(segment.path)}>{segment.name}</button></BreadcrumbLink>
                  )}
                </BreadcrumbItem>
              </Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>
        <Button variant="outline" size="sm" onClick={() => navigateTo(parentOf(currentPath))} disabled={atRoot || noAgent}>
          <ArrowUp />上一级
        </Button>
      </div>

      <div className="flex shrink-0 flex-wrap items-center gap-1.5 rounded-lg border bg-muted/20 p-1.5">
        <input ref={uploadRef} type="file" multiple className="hidden" onChange={handleUploadSelect} />
        {!touchMode ? (
          <>
            <Button variant="ghost" size="sm" onClick={() => openCreate("DIR")} disabled={noAgent}><FolderPlus />新建文件夹</Button>
            <Button variant="ghost" size="sm" onClick={() => openCreate("FILE")} disabled={noAgent}><FilePlus />新建文件</Button>
            <Button variant="ghost" size="sm" onClick={() => uploadRef.current?.click()} disabled={noAgent}><Upload />上传</Button>
            <div className="mx-1 h-5 w-px bg-border" />
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => setClipboardFor("copy", selectedEntries)}><Copy />复制</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => setClipboardFor("cut", selectedEntries)}><Scissors />剪切</Button>
            <Button variant="ghost" size="sm" disabled={!canPaste || operationPending} onClick={() => handlePaste(currentPath)}><ClipboardPaste />粘贴</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length !== 1 || operationPending} onClick={() => openRename(selectedEntries[0])}><Pencil />重命名</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0 || downloading} onClick={() => handleDownload(selectedEntries)}>
              {downloading ? <Loader2 className="animate-spin" /> : <Download />}下载
            </Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0 || operationPending} onClick={() => setDeleteEntries(selectedEntries)}><Trash2 />删除</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => openProperties(selectedEntries)}><Info />属性</Button>
          </>
        ) : selectionMode ? (
          <>
            <span className="px-2 text-sm font-medium whitespace-nowrap">已选 {selectedEntries.length} 项</span>
            <Button variant="ghost" size="sm" disabled={entries.length === 0} onClick={selectAll}><ListChecks />全选</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => setClipboardFor("copy", selectedEntries)}><Copy />复制</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => setClipboardFor("cut", selectedEntries)}><Scissors />剪切</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0 || downloading} onClick={() => handleDownload(selectedEntries)}><Download />下载</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0 || operationPending} onClick={() => setDeleteEntries(selectedEntries)}><Trash2 />删除</Button>
            <Button variant="ghost" size="sm" disabled={selectedEntries.length === 0} onClick={() => openProperties(selectedEntries)}><Info />属性</Button>
            <Button variant="outline" size="sm" className="ms-auto" onClick={exitSelectionMode}><Check />完成</Button>
          </>
        ) : (
          <>
            <Button variant="ghost" size="sm" onClick={() => uploadRef.current?.click()} disabled={noAgent}><Upload />上传</Button>
            <Button variant="ghost" size="sm" onClick={() => enterSelectionMode()} disabled={noAgent || entries.length === 0}><ListChecks />选择</Button>
            <Button variant="ghost" size="sm" onClick={openMobileDirectoryMenu} disabled={noAgent}><MoreVertical />更多</Button>
          </>
        )}
        <div className={selectionMode && touchMode ? "hidden" : "ms-auto"}>
          <DropdownMenu>
            <DropdownMenuTrigger asChild><Button variant="outline" size="sm"><Eye />查看</Button></DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuRadioGroup value={viewMode} onValueChange={(value) => setViewMode(value as WorkspaceViewMode)}>
                <DropdownMenuRadioItem value="large"><LayoutGrid />大图标</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="medium"><Grid2X2 />中图标</DropdownMenuRadioItem>
                <DropdownMenuRadioItem value="details"><List />详细</DropdownMenuRadioItem>
              </DropdownMenuRadioGroup>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <ContextMenu
        open={touchMode ? false : desktopMenuOpen}
        onOpenChange={(open) => { if (!touchMode) setDesktopMenuOpen(open); }}
      >
        <ContextMenuTrigger asChild>
          <div
            className="min-h-0 flex-1 overflow-auto rounded-lg border bg-background outline-none focus-visible:ring-2 focus-visible:ring-ring/30"
            tabIndex={0}
            onKeyDown={handleListKeyDown}
            onClick={(event) => {
              if (!touchMode && !(event.target as Element).closest("[data-workspace-entry]")) clearSelection();
            }}
            onContextMenu={(event) => {
              if (touchMode) event.preventDefault();
              if (!(event.target as Element).closest("[data-workspace-entry]")) setContextEntry(null);
            }}
          >
            {noAgent ? (
              <div className="flex h-full items-center justify-center text-muted-foreground">暂无智能体，请先在「智能体」中创建</div>
            ) : loading && entries.length === 0 ? (
              <div className="flex h-full items-center justify-center text-muted-foreground">加载中...</div>
            ) : entries.length === 0 ? (
              <div className="flex h-full items-center justify-center text-muted-foreground">
                {touchMode ? "空目录，可通过“更多”新建或上传文件" : "空目录，可右键新建或上传文件"}
              </div>
            ) : (
              <WorkspaceEntryView
                entries={entries}
                viewMode={viewMode}
                selectedPaths={selectedPaths}
                cutPaths={cutPaths}
                touchMode={touchMode}
                selectionMode={selectionMode}
                onEntryClick={handleEntryClick}
                onToggleSelect={(entry) => toggleSelection(entry.path)}
                onOpen={openEntry}
                onContextEntry={handleContextEntry}
                onLongPress={handleLongPressEntry}
              />
            )}
          </div>
        </ContextMenuTrigger>
        <ContextMenuContent>
          {contextEntry ? (
            <>
              <ContextMenuLabel>{contextTargets.length > 1 ? `已选择 ${contextTargets.length} 个项目` : contextEntry.name}</ContextMenuLabel>
              <ContextMenuSeparator />
              {contextTargets.length === 1 && <ContextMenuItem onSelect={() => openEntry(contextEntry)}>打开</ContextMenuItem>}
              <ContextMenuItem onSelect={() => handleDownload(contextTargets)}><Download />下载</ContextMenuItem>
              <ContextMenuItem onSelect={() => setClipboardFor("copy", contextTargets)}><Copy />复制</ContextMenuItem>
              <ContextMenuItem onSelect={() => setClipboardFor("cut", contextTargets)}><Scissors />剪切</ContextMenuItem>
              <ContextMenuItem disabled={!canPaste || operationPending} onSelect={() => handlePaste(contextEntry.directory ? contextEntry.path : currentPath)}>
                <ClipboardPaste />粘贴{contextEntry.directory ? "到此文件夹" : ""}
              </ContextMenuItem>
              {contextTargets.length === 1 && <ContextMenuItem onSelect={() => openRename(contextEntry)}><Pencil />重命名</ContextMenuItem>}
              <ContextMenuSeparator />
              <ContextMenuItem variant="destructive" disabled={operationPending} onSelect={() => setDeleteEntries(contextTargets)}><Trash2 />删除</ContextMenuItem>
              <ContextMenuItem onSelect={() => openProperties(contextTargets)}><Info />属性</ContextMenuItem>
            </>
          ) : (
            <>
              <ContextMenuItem disabled={!canPaste || operationPending} onSelect={() => handlePaste(currentPath)}><ClipboardPaste />粘贴</ContextMenuItem>
              <ContextMenuSeparator />
              <ContextMenuItem onSelect={() => openCreate("DIR")}><FolderPlus />新建文件夹</ContextMenuItem>
              <ContextMenuItem onSelect={() => openCreate("FILE")}><FilePlus />新建文件</ContextMenuItem>
              <ContextMenuItem onSelect={() => uploadRef.current?.click()}><Upload />上传</ContextMenuItem>
              <ContextMenuItem onSelect={fetchEntries}><RefreshCw />刷新</ContextMenuItem>
            </>
          )}
        </ContextMenuContent>
      </ContextMenu>

      <WorkspaceTouchActionSheet
        open={touchMode && mobileSheetOpen}
        entry={contextEntry}
        targetCount={contextTargets.length}
        canPaste={canPaste}
        operationPending={operationPending}
        onOpenChange={setMobileSheetOpen}
        onOpen={() => contextEntry && openEntry(contextEntry)}
        onDownload={() => handleDownload(contextTargets)}
        onCopy={() => setClipboardFor("copy", contextTargets)}
        onCut={() => setClipboardFor("cut", contextTargets)}
        onPaste={() => handlePaste(contextEntry?.directory ? contextEntry.path : currentPath)}
        onRename={() => contextEntry && openRename(contextEntry)}
        onDelete={() => setDeleteEntries(contextTargets)}
        onProperties={() => openProperties(contextTargets)}
        onSelect={handleMobileSelect}
        onCreate={openCreate}
        onUpload={() => uploadRef.current?.click()}
        onRefresh={fetchEntries}
      />

      <div className="shrink-0 text-xs text-muted-foreground">
        {entries.length} 个项目{selectedEntries.length > 0 ? `，已选择 ${selectedEntries.length} 个` : ""}
        <span className="ms-2 hidden sm:inline">
          {touchMode
            ? selectionMode ? "单击切换选择，点击完成退出多选" : "单击打开，长按更多操作"
            : "单击选择，双击打开；Ctrl 多选，Shift 连续选择"}
        </span>
      </div>

      {/* 预览 / 编辑 */}
      <Dialog open={!!previewEntry} onOpenChange={(open) => { if (!open) closePreview(); }}>
        <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden sm:max-w-6xl">
          <DialogHeader><DialogTitle className="break-all">{previewEntry?.path}</DialogTitle></DialogHeader>
          {previewLoading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : previewError ? (
            <div className="py-8 text-center text-destructive">{previewError}</div>
          ) : previewContent?.kind === "IMAGE" ? (
            <div className="flex justify-center overflow-y-auto">
              <img src={previewContent.dataUrl ?? undefined} alt={previewEntry?.name} className="max-h-[70vh] rounded" />
            </div>
          ) : previewContent?.kind === "TEXT" ? (
            <>
              <div className="min-h-0 flex-1 overflow-auto">
                {previewMode === "view" ? (
                  <MarkdownRenderer content={MD_EXTS.has(extensionOf(previewEntry?.name ?? ""))
                    ? previewContent.content ?? ""
                    : codeFence(previewEntry?.name ?? "", previewContent.content ?? "")} />
                ) : (
                  <Textarea rows={20} className="resize-y font-mono text-sm" value={editText}
                    onChange={(event) => { setEditText(event.target.value); setEditModified(true); }} />
                )}
              </div>
              <DialogFooter>
                {previewMode === "view" ? (
                  <><Button variant="outline" onClick={closePreview}>关闭</Button><Button onClick={() => setPreviewMode("edit")}>编辑</Button></>
                ) : (
                  <><Button variant="outline" onClick={() => setPreviewMode("view")}>预览</Button>
                    <Button onClick={handleSave} disabled={editSaving || !editModified}>{editSaving ? "保存中..." : "保存"}</Button></>
                )}
              </DialogFooter>
            </>
          ) : (
            <div className="py-8 text-center text-muted-foreground">该文件类型不支持在线预览</div>
          )}
        </DialogContent>
      </Dialog>

      {/* 新建 */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader><DialogTitle>{createType === "DIR" ? "新建文件夹" : "新建文件"}</DialogTitle></DialogHeader>
          <div className="flex flex-col gap-2">
            <Label>名称{createType === "DIR" ? "" : "（含扩展名，如 notes.md）"}</Label>
            <Input value={createName} onChange={(event) => setCreateName(event.target.value)}
              placeholder={createType === "DIR" ? "folder-name" : "notes.md"}
              onKeyDown={(event) => { if (event.key === "Enter") handleCreate(); }} autoFocus />
          </div>
          <DialogFooter><Button variant="outline" onClick={() => setCreateOpen(false)}>取消</Button>
            <Button onClick={handleCreate} disabled={createSaving}>{createSaving ? "创建中..." : "确定"}</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 重命名 */}
      <Dialog open={!!renameEntry} onOpenChange={(open) => { if (!open && !renaming) setRenameEntry(null); }}>
        <DialogContent className="max-w-lg">
          <DialogHeader><DialogTitle>重命名</DialogTitle><DialogDescription>输入新的文件或文件夹名称。</DialogDescription></DialogHeader>
          <Input value={renameValue} onChange={(event) => setRenameValue(event.target.value)} autoFocus
            onKeyDown={(event) => { if (event.key === "Enter") handleRename(); }} />
          <DialogFooter><Button variant="outline" onClick={() => setRenameEntry(null)} disabled={renaming}>取消</Button>
            <Button onClick={handleRename} disabled={renaming || !renameValue.trim()}>{renaming ? "处理中..." : "确定"}</Button></DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认 */}
      <AlertDialog open={deleteEntries.length > 0} onOpenChange={(open) => { if (!open && !deleting) setDeleteEntries([]); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除 {deleteEntries.length > 1 ? `选中的 ${deleteEntries.length} 个项目` : `「${deleteEntries[0]?.name}」`} 吗？
              {deleteEntries.some((entry) => entry.directory) ? "文件夹及其所有内容将被一并删除，" : ""}此操作不可撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter><AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} disabled={deleting}>{deleting ? "删除中..." : "删除"}</AlertDialogAction></AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 属性 */}
      <Dialog open={propertiesEntries.length > 0} onOpenChange={(open) => { if (!open) closeProperties(); }}>
        <DialogContent className="max-w-lg">
          <DialogHeader><DialogTitle>属性</DialogTitle><DialogDescription>
            {propertiesEntries.length === 1 ? propertiesEntries[0].name : `选中的 ${propertiesEntries.length} 个项目`}
          </DialogDescription></DialogHeader>
          {propertiesLoading ? (
            <div className="flex justify-center py-8"><Loader2 className="animate-spin" /></div>
          ) : properties && (
            <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-3 text-sm">
              <dt className="text-muted-foreground">位置</dt><dd className="break-all">{currentPath || "工作区根目录"}</dd>
              {propertiesEntries.length === 1 && <><dt className="text-muted-foreground">类型</dt><dd>{fileTypeLabel(propertiesEntries[0].name, propertiesEntries[0].directory)}</dd></>}
              <dt className="text-muted-foreground">文件</dt><dd>{properties.files.toLocaleString()} 个</dd>
              <dt className="text-muted-foreground">文件夹</dt><dd>{properties.directories.toLocaleString()} 个</dd>
              <dt className="text-muted-foreground">总大小</dt><dd>{formatFileSize(properties.totalSize)}</dd>
              <dt className="text-muted-foreground">修改时间</dt><dd>{properties.lastModified ? new Date(properties.lastModified).toLocaleString() : "-"}</dd>
            </dl>
          )}
        </DialogContent>
      </Dialog>

      <WorkspaceConflictDialog
        key={conflictName ?? "no-conflict"}
        name={conflictName}
        showApplyAll={conflictAllowApplyAll}
        onChoose={chooseConflict}
      />
    </div>
  );
}
