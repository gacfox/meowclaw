import React, { useEffect, useMemo, useState, useCallback } from "react";
import {
  ArrowLeft,
  Eye,
  Folder,
  File,
  Download,
  Trash2,
  Move,
  X,
  FolderOpen,
  ChevronRight,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Spinner } from "@/components/ui/spinner";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from "@/components/ui/dialog";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
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
import { workspaceService, type WorkspaceEntryDto } from "@/services/workspace";
import { useAppStore } from "@/stores/appStore";

interface PreviewState {
  open: boolean;
  title: string;
  kind: "text" | "image" | "video" | "audio" | "other";
  content?: string;
  url?: string;
}

interface FolderNode {
  name: string;
  path: string;
  children: FolderNode[];
}

const formatSize = (size: number) => {
  if (size < 1024) return `${size} B`;
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`;
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  return `${(size / 1024 / 1024 / 1024).toFixed(1)} GB`;
};

const isPreviewableText = (mime?: string | null, name?: string) => {
  if (
    mime &&
    (mime.startsWith("text/") || mime.includes("json") || mime.includes("xml"))
  ) {
    return true;
  }
  if (!name) return false;
  const lower = name.toLowerCase();
  return (
    lower.endsWith(".md") ||
    lower.endsWith(".txt") ||
    lower.endsWith(".log") ||
    lower.endsWith(".json") ||
    lower.endsWith(".xml")
  );
};

const isImage = (mime?: string | null, name?: string) => {
  if (mime && mime.startsWith("image/")) return true;
  if (!name) return false;
  return /\.(png|jpg|jpeg|gif|webp|bmp|svg)$/i.test(name);
};

const isVideo = (mime?: string | null, name?: string) => {
  if (mime && mime.startsWith("video/")) return true;
  if (!name) return false;
  return /\.(mp4|webm|mov|mkv|avi)$/i.test(name);
};

const isAudio = (mime?: string | null, name?: string) => {
  if (mime && mime.startsWith("audio/")) return true;
  if (!name) return false;
  return /\.(mp3|wav|ogg|aac|flac)$/i.test(name);
};

const isPreviewable = (entry: WorkspaceEntryDto) => {
  if (entry.directory) return false;
  const mime = entry.mime;
  const name = entry.name;
  return (
    isPreviewableText(mime, name) ||
    isImage(mime, name) ||
    isVideo(mime, name) ||
    isAudio(mime, name)
  );
};

const buildFolderTree = (entries: WorkspaceEntryDto[]): FolderNode[] => {
  const root: FolderNode[] = [];
  const map = new Map<string, FolderNode>();

  const dirs = entries
    .filter((e) => e.directory)
    .sort((a, b) => a.path.localeCompare(b.path));

  for (const dir of dirs) {
    const node: FolderNode = {
      name: dir.name,
      path: dir.path,
      children: [],
    };
    map.set(dir.path, node);

    const parentPath = dir.path.includes("/")
      ? dir.path.slice(0, dir.path.lastIndexOf("/"))
      : "";
    if (!parentPath) {
      root.push(node);
    } else {
      const parent = map.get(parentPath);
      if (parent) {
        parent.children.push(node);
      }
    }
  }

  return root;
};

export const WorkspaceManager: React.FC = () => {
  const { lastSelectedAgentId, setLastSelectedAgentId } = useAppStore();
  const [agents, setAgents] = useState<AgentConfigDto[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [entries, setEntries] = useState<WorkspaceEntryDto[]>([]);
  const [path, setPath] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [preview, setPreview] = useState<PreviewState>({
    open: false,
    title: "",
    kind: "other",
  });
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingEntries, setDeletingEntries] = useState<WorkspaceEntryDto[]>(
    [],
  );
  const [moveDialogOpen, setMoveDialogOpen] = useState(false);
  const [moveTargetPath, setMoveTargetPath] = useState("");
  const [expandedFolders, setExpandedFolders] = useState<Set<string>>(
    new Set(),
  );
  const [selectedPaths, setSelectedPaths] = useState<Set<string>>(new Set());
  const [previewUnavailableOpen, setPreviewUnavailableOpen] = useState(false);

  useEffect(() => {
    agentConfigService.list().then((res) => {
      if (res.code === 200 && res.data) {
        setAgents(res.data);
        const lastAgent = lastSelectedAgentId
          ? res.data.find((a) => a.id === lastSelectedAgentId)
          : null;
        const defaultId = lastAgent?.id || res.data[0]?.id;
        if (defaultId) {
          setSelectedAgentId(defaultId);
        }
      }
    });
  }, []);

  useEffect(() => {
    if (selectedAgentId) {
      loadEntries(selectedAgentId, path);
    }
  }, [selectedAgentId, path]);

  useEffect(() => {
    setSelectedPaths(new Set());
  }, [path]);

  const loadEntries = async (agentId: number, currentPath: string) => {
    setIsLoading(true);
    try {
      const response = await workspaceService.list(agentId, currentPath);
      if (response.code === 200 && response.data) {
        setEntries(response.data);
      }
    } finally {
      setIsLoading(false);
    }
  };

  const breadcrumbs = useMemo(() => {
    const parts = path ? path.split("/").filter(Boolean) : [];
    const result = [{ label: "工作区", value: "" }];
    let acc = "";
    parts.forEach((part) => {
      acc = acc ? `${acc}/${part}` : part;
      result.push({ label: part, value: acc });
    });
    return result;
  }, [path]);

  const goTo = (nextPath: string) => {
    setPath(nextPath);
  };

  const selectedEntries = useMemo(() => {
    return entries.filter((e) => selectedPaths.has(e.path));
  }, [entries, selectedPaths]);

  const handleRowClick = (entry: WorkspaceEntryDto, e: React.MouseEvent) => {
    if (e.ctrlKey || e.metaKey) {
      setSelectedPaths((prev) => {
        const next = new Set(prev);
        if (next.has(entry.path)) {
          next.delete(entry.path);
        } else {
          next.add(entry.path);
        }
        return next;
      });
    } else if (selectedPaths.has(entry.path) && selectedPaths.size === 1) {
      clearSelection();
    } else {
      setSelectedPaths(new Set([entry.path]));
    }
  };

  const handleRowDoubleClick = (entry: WorkspaceEntryDto) => {
    if (entry.directory) {
      goTo(entry.path);
    } else if (isPreviewable(entry)) {
      handlePreview(entry);
    } else {
      setPreviewUnavailableOpen(true);
    }
  };

  const clearSelection = () => {
    setSelectedPaths(new Set());
  };

  const handleDelete = (entries: WorkspaceEntryDto[]) => {
    if (!selectedAgentId || entries.length === 0) return;
    setDeletingEntries(entries);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!selectedAgentId || deletingEntries.length === 0) return;
    for (const entry of deletingEntries) {
      await workspaceService.delete(selectedAgentId, entry.path);
    }
    loadEntries(selectedAgentId, path);
    setDeleteDialogOpen(false);
    setDeletingEntries([]);
    setSelectedPaths(new Set());
  };

  const handleMove = (entries: WorkspaceEntryDto[]) => {
    if (!selectedAgentId || entries.length === 0) return;
    setMoveTargetPath(path);
    setExpandedFolders(new Set([path]));
    setMoveDialogOpen(true);
  };

  const confirmMove = async () => {
    if (!selectedAgentId || selectedEntries.length === 0) return;
    for (const entry of selectedEntries) {
      const targetPath = moveTargetPath
        ? `${moveTargetPath}/${entry.name}`
        : entry.name;
      if (entry.path !== targetPath) {
        await workspaceService.move(selectedAgentId, entry.path, targetPath);
      }
    }
    loadEntries(selectedAgentId, path);
    setMoveDialogOpen(false);
    setSelectedPaths(new Set());
  };

  const handleDownload = async (entries: WorkspaceEntryDto[]) => {
    if (!selectedAgentId) return;
    for (const entry of entries) {
      if (entry.directory) continue;
      const response = await workspaceService.download(
        selectedAgentId,
        entry.path,
      );
      if (!response.ok) continue;
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = entry.name;
      link.click();
      window.URL.revokeObjectURL(url);
    }
  };

  const handlePreview = async (entry: WorkspaceEntryDto) => {
    if (!selectedAgentId) return;
    const response = await workspaceService.download(
      selectedAgentId,
      entry.path,
    );
    if (!response.ok) return;
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const mime = blob.type || entry.mime;
    if (isPreviewableText(mime, entry.name)) {
      const text = await blob.text();
      setPreview({
        open: true,
        title: entry.name,
        kind: "text",
        content: text,
      });
      window.URL.revokeObjectURL(url);
      return;
    }
    if (isImage(mime, entry.name)) {
      setPreview({ open: true, title: entry.name, kind: "image", url });
      return;
    }
    if (isVideo(mime, entry.name)) {
      setPreview({ open: true, title: entry.name, kind: "video", url });
      return;
    }
    if (isAudio(mime, entry.name)) {
      setPreview({ open: true, title: entry.name, kind: "audio", url });
      return;
    }
    setPreview({ open: true, title: entry.name, kind: "other" });
    window.URL.revokeObjectURL(url);
  };

  const closePreview = () => {
    if (preview.url) {
      window.URL.revokeObjectURL(preview.url);
    }
    setPreview({ open: false, title: "", kind: "other" });
  };

  const toggleFolderExpand = (folderPath: string) => {
    setExpandedFolders((prev) => {
      const next = new Set(prev);
      if (next.has(folderPath)) {
        next.delete(folderPath);
      } else {
        next.add(folderPath);
      }
      return next;
    });
  };

  const allFolders = useMemo(() => {
    return entries.filter((e) => e.directory);
  }, [entries]);

  const folderTree = useMemo(() => buildFolderTree(allFolders), [allFolders]);

  const renderFolderTree = useCallback(
    (nodes: FolderNode[], level: number = 0): React.ReactNode => {
      return nodes.map((node) => {
        const isExpanded = expandedFolders.has(node.path);
        const isSelected = moveTargetPath === node.path;
        return (
          <div key={node.path}>
            <div
              className={`flex items-center gap-1 py-1.5 px-2 cursor-pointer rounded-md hover:bg-accent ${isSelected ? "bg-primary/10" : ""}`}
              style={{ paddingLeft: `${level * 16 + 8}px` }}
              onClick={() => setMoveTargetPath(node.path)}
              onDoubleClick={() => toggleFolderExpand(node.path)}
            >
              <button
                className="p-0.5 hover:bg-accent rounded"
                onClick={(e) => {
                  e.stopPropagation();
                  toggleFolderExpand(node.path);
                }}
              >
                <ChevronRight
                  className={`h-4 w-4 transition-transform ${isExpanded ? "rotate-90" : ""}`}
                />
              </button>
              {isExpanded ? (
                <FolderOpen className="h-4 w-4 text-primary" />
              ) : (
                <Folder className="h-4 w-4 text-primary" />
              )}
              <span className="text-sm truncate">{node.name}</span>
            </div>
            {isExpanded && node.children.length > 0 && (
              <div>{renderFolderTree(node.children, level + 1)}</div>
            )}
          </div>
        );
      });
    },
    [expandedFolders, moveTargetPath],
  );

  return (
    <div className="p-6 h-full flex flex-col min-h-0">
      <div className="flex items-center justify-between gap-4 mb-4">
        <div>
          <h1 className="text-2xl font-bold">工作区</h1>
          <p className="text-sm text-muted-foreground mt-1">管理智能体工作空间文件</p>
        </div>
        <Select
          value={selectedAgentId?.toString() || ""}
          onValueChange={(value) => {
            const id = parseInt(value);
            setSelectedAgentId(Number.isNaN(id) ? null : id);
            if (!Number.isNaN(id)) {
              setLastSelectedAgentId(id);
            }
            setPath("");
          }}
        >
          <SelectTrigger className="w-56">
            <SelectValue placeholder="选择智能体" />
          </SelectTrigger>
          <SelectContent>
            {agents.map((agent) => (
              <SelectItem key={agent.id} value={agent.id?.toString() || ""}>
                {agent.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="flex items-center gap-2 mb-3">
        <Button
          variant="outline"
          size="icon"
          disabled={!path}
          onClick={() => {
            const idx = path.lastIndexOf("/");
            if (idx === -1) {
              setPath("");
            } else {
              setPath(path.slice(0, idx));
            }
          }}
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <Breadcrumb>
          <BreadcrumbList>
            {breadcrumbs.map((crumb, index) => (
              <React.Fragment key={crumb.value || "root"}>
                {index > 0 && <BreadcrumbSeparator />}
                <BreadcrumbItem>
                  <BreadcrumbLink
                    asChild
                    className="cursor-pointer"
                    onClick={() => goTo(crumb.value)}
                  >
                    <span>{crumb.label}</span>
                  </BreadcrumbLink>
                </BreadcrumbItem>
              </React.Fragment>
            ))}
          </BreadcrumbList>
        </Breadcrumb>
      </div>

      {/* 操作面板 */}
      <div className="flex items-center gap-3 mb-3 p-3 bg-muted/50 rounded-lg border min-h-13.5">
        <span className="text-sm font-medium">
          已选择 {selectedEntries.length} 项
        </span>
        {selectedEntries.length > 0 && (
          <div className="flex items-center gap-2 ml-auto">
            {selectedEntries.length === 1 && !selectedEntries[0].directory && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => handlePreview(selectedEntries[0])}
              >
                <Eye className="h-4 w-4 mr-1" />
                预览
              </Button>
            )}
            {selectedEntries.some((e) => !e.directory) && (
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  handleDownload(selectedEntries.filter((e) => !e.directory))
                }
              >
                <Download className="h-4 w-4 mr-1" />
                下载
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleMove(selectedEntries)}
            >
              <Move className="h-4 w-4 mr-1" />
              移动
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={() => handleDelete(selectedEntries)}
            >
              <Trash2 className="h-4 w-4 mr-1" />
              删除
            </Button>
            <Button variant="ghost" size="sm" onClick={clearSelection}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        )}
      </div>

      <div className="border rounded-lg flex-1 min-h-0">
        <ScrollArea className="h-full">
          <div className="min-w-180">
            <div className="grid grid-cols-[2fr_1fr_1fr] gap-3 border-b px-4 py-2 text-xs text-muted-foreground bg-muted/30">
              <span>名称</span>
              <span>大小</span>
              <span>修改时间</span>
            </div>
            {isLoading ? (
              <div className="flex items-center justify-center py-12">
                <Spinner className="size-6" />
              </div>
            ) : entries.length === 0 ? (
              <div className="px-4 py-6 text-sm text-muted-foreground">
                目录为空
              </div>
            ) : (
              entries.map((entry) => {
                const isSelected = selectedPaths.has(entry.path);
                return (
                  <div
                    key={entry.path}
                    data-entry-row
                    className={`grid grid-cols-[2fr_1fr_1fr] gap-3 items-center border-b px-4 py-3 text-sm cursor-pointer transition-colors ${
                      isSelected ? "bg-primary/10" : "hover:bg-muted/50"
                    }`}
                    onClick={(e) => handleRowClick(entry, e)}
                    onDoubleClick={() => handleRowDoubleClick(entry)}
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      {entry.directory ? (
                        <Folder className="h-4 w-4 text-primary shrink-0" />
                      ) : (
                        <File className="h-4 w-4 text-muted-foreground shrink-0" />
                      )}
                      <span className="truncate">{entry.name}</span>
                    </div>
                    <span className="text-muted-foreground">
                      {entry.directory ? "-" : formatSize(entry.size)}
                    </span>
                    <span className="text-muted-foreground">
                      {entry.modifiedAt
                        ? new Date(entry.modifiedAt).toLocaleString()
                        : "-"}
                    </span>
                  </div>
                );
              })
            )}
          </div>
        </ScrollArea>
      </div>

      {/* 预览对话框 */}
      <Dialog
        open={preview.open}
        onOpenChange={(open) => !open && closePreview()}
      >
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-hidden">
          <DialogHeader>
            <DialogTitle>{preview.title}</DialogTitle>
          </DialogHeader>
          <div className="mt-2 max-h-[70vh] overflow-auto">
            {preview.kind === "text" && (
              <pre className="whitespace-pre-wrap text-sm bg-muted/40 rounded-md p-4">
                {preview.content}
              </pre>
            )}
            {preview.kind === "image" && preview.url && (
              <img
                src={preview.url}
                alt={preview.title}
                className="max-w-full"
              />
            )}
            {preview.kind === "video" && preview.url && (
              <video src={preview.url} controls className="w-full" />
            )}
            {preview.kind === "audio" && preview.url && (
              <audio src={preview.url} controls className="w-full" />
            )}
            {preview.kind === "other" && (
              <div className="text-sm text-muted-foreground">
                当前文件类型暂不支持预览，请下载查看。
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>

      {/* 移动对话框 */}
      <Dialog open={moveDialogOpen} onOpenChange={setMoveDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>移动到</DialogTitle>
          </DialogHeader>
          <div className="border rounded-md max-h-64 overflow-auto">
            <div
              className={`flex items-center gap-2 py-1.5 px-2 cursor-pointer rounded-md hover:bg-accent ${moveTargetPath === "" ? "bg-primary/10" : ""}`}
              onClick={() => setMoveTargetPath("")}
            >
              <Folder className="h-4 w-4 text-primary" />
              <span className="text-sm">工作区根目录</span>
            </div>
            {renderFolderTree(folderTree)}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoveDialogOpen(false)}>
              取消
            </Button>
            <Button onClick={confirmMove}>确定</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* 删除确认对话框 */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除</AlertDialogTitle>
            <AlertDialogDescription>
              确定要删除选中的 {deletingEntries.length} 项吗？此操作无法撤销。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* 预览不可用提示 */}
      <AlertDialog
        open={previewUnavailableOpen}
        onOpenChange={setPreviewUnavailableOpen}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>无法预览</AlertDialogTitle>
            <AlertDialogDescription>
              该文件类型暂不支持预览，请下载查看。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogAction>确定</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
};
