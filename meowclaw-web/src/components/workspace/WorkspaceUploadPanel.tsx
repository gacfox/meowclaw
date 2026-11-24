import { useState } from "react";
import {
  ChevronDown,
  ChevronUp,
  CircleAlert,
  Pause,
  Play,
  Trash2,
  Upload,
  X,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  useWorkspaceUpload,
  type WorkspaceUploadStatus,
  type WorkspaceUploadTask,
} from "@/context/WorkspaceUploadContext";
import { cn } from "@/lib/utils";
import type { ConflictStrategy } from "@/types";

const ACTIVE_STATUSES: WorkspaceUploadStatus[] = ["QUEUED", "UPLOADING", "PAUSED", "CONFLICT"];

const STATUS_LABELS: Record<WorkspaceUploadStatus, string> = {
  QUEUED: "等待中",
  UPLOADING: "上传中",
  PAUSED: "已暂停",
  CONFLICT: "待处理冲突",
  FAILED: "上传失败",
  COMPLETED: "已完成",
};

function formatBytes(bytes: number) {
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  const unitIndex = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1);
  const value = bytes / 1024 ** unitIndex;
  const formatted = unitIndex === 0 ? String(Math.round(value)) : value.toFixed(value >= 10 ? 0 : 1);
  return `${formatted} ${units[unitIndex]}`;
}

function taskProgress(task: WorkspaceUploadTask) {
  if (task.status === "COMPLETED") return 100;
  if (task.file.size === 0) return 0;
  return Math.min(100, Math.max(0, (task.uploadedBytes / task.file.size) * 100));
}

function ConflictDialog({
  task,
  onResolve,
  onCancel,
}: {
  task: WorkspaceUploadTask;
  onResolve: (strategy: ConflictStrategy, applyToFuture: boolean) => void;
  onCancel: () => void;
}) {
  const [applyToFuture, setApplyToFuture] = useState(false);

  return (
    <Dialog open>
      <DialogContent
        showCloseButton={false}
        onEscapeKeyDown={(event) => event.preventDefault()}
        onPointerDownOutside={(event) => event.preventDefault()}
      >
        <DialogHeader>
          <DialogTitle>目标位置已存在同名文件</DialogTitle>
          <DialogDescription>
            <span className="block break-all font-medium text-foreground">{task.file.name}</span>
            <span className="mt-1 block break-all">目录：{task.targetDir || "/"}</span>
            <span className="mt-2 block">请选择如何处理此文件，上传队列会在处理完成后继续。</span>
          </DialogDescription>
        </DialogHeader>
        <label className="flex cursor-pointer items-center gap-2 text-sm">
          <input
            type="checkbox"
            className="size-4 accent-primary"
            checked={applyToFuture}
            onChange={(event) => setApplyToFuture(event.target.checked)}
          />
          应用到后续冲突
        </label>
        <DialogFooter className="gap-2 sm:flex-wrap">
          <Button type="button" variant="ghost" onClick={onCancel}>
            取消上传
          </Button>
          <Button type="button" variant="outline" onClick={() => onResolve("SKIP", applyToFuture)}>
            跳过
          </Button>
          <Button type="button" variant="outline" onClick={() => onResolve("RENAME", applyToFuture)}>
            保留两者
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={() => onResolve("OVERWRITE", applyToFuture)}
          >
            覆盖
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

export function WorkspaceUploadPanel() {
  const {
    tasks,
    pause,
    resume,
    cancel,
    dismiss,
    clearFinished,
    resolveConflict,
  } = useWorkspaceUpload();
  const [expanded, setExpanded] = useState(true);

  const conflictTask = tasks.find((task) => task.status === "CONFLICT");
  if (tasks.length === 0) return null;

  const activeCount = tasks.filter((task) => ACTIVE_STATUSES.includes(task.status)).length;
  const finishedCount = tasks.filter(
    (task) => task.status === "COMPLETED" || task.status === "FAILED",
  ).length;

  return (
    <>
      <aside
        aria-label="全局上传队列"
        className="fixed right-3 bottom-3 z-40 w-[calc(100vw-1.5rem)] overflow-hidden rounded-xl border bg-card/95 text-card-foreground shadow-xl backdrop-blur sm:right-5 sm:bottom-5 sm:w-96"
      >
        <button
          type="button"
          className="flex w-full items-center gap-2 border-b px-3 py-2.5 text-left outline-none transition-colors hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring/50"
          onClick={() => setExpanded((value) => !value)}
          aria-expanded={expanded}
        >
          <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
            <Upload className="size-4" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-sm font-medium">上传队列</span>
            <span className="block text-xs text-muted-foreground">
              {activeCount > 0 ? `${activeCount} 个任务进行中` : "所有任务已结束"} · 共 {tasks.length} 项
            </span>
          </span>
          {expanded ? <ChevronDown className="size-4" /> : <ChevronUp className="size-4" />}
        </button>

        {expanded && (
          <>
            <div className="max-h-[min(52vh,24rem)] divide-y overflow-y-auto">
              {tasks.map((task) => {
                const progress = taskProgress(task);
                const statusLabel = task.finalizing
                  ? "合并中"
                  : task.status === "COMPLETED" && task.skipped
                    ? "已跳过"
                    : STATUS_LABELS[task.status];

                return (
                  <div key={task.id} className="space-y-2 px-3 py-2.5">
                    <div className="flex items-start gap-2">
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-2">
                          <span className="truncate text-sm font-medium" title={task.file.name}>
                            {task.file.name}
                          </span>
                          <span
                            className={cn(
                              "shrink-0 rounded-full bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground",
                              task.status === "FAILED" && "bg-destructive/10 text-destructive",
                              task.status === "CONFLICT" && "bg-amber-500/10 text-amber-600",
                              task.status === "COMPLETED" && "bg-primary/10 text-primary",
                            )}
                          >
                            {statusLabel}
                          </span>
                        </div>
                        <div
                          className="mt-0.5 truncate text-xs text-muted-foreground"
                          title={task.targetDir || "/"}
                        >
                          上传至 {task.targetDir || "/"}
                        </div>
                      </div>
                      <div className="flex shrink-0 items-center gap-0.5">
                        {!task.finalizing && (task.status === "QUEUED" || task.status === "UPLOADING") && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-xs"
                            title="暂停"
                            aria-label={`暂停 ${task.file.name}`}
                            onClick={() => pause(task.id)}
                          >
                            <Pause />
                          </Button>
                        )}
                        {(task.status === "PAUSED" || task.status === "FAILED") && (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-xs"
                            title="继续"
                            aria-label={`继续 ${task.file.name}`}
                            onClick={() => resume(task.id)}
                          >
                            <Play />
                          </Button>
                        )}
                        {!task.finalizing && (task.status !== "COMPLETED" ? (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-xs"
                            title="取消"
                            aria-label={`取消 ${task.file.name}`}
                            onClick={() => cancel(task.id)}
                          >
                            <X />
                          </Button>
                        ) : (
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon-xs"
                            title="移除"
                            aria-label={`移除 ${task.file.name}`}
                            onClick={() => dismiss(task.id)}
                          >
                            <Trash2 />
                          </Button>
                        ))}
                      </div>
                    </div>

                    <div className="flex items-center justify-between gap-2 text-[11px] text-muted-foreground">
                      <span>
                        {formatBytes(task.uploadedBytes)} / {formatBytes(task.file.size)}
                      </span>
                      <span>{Math.floor(progress)}%</span>
                    </div>
                    <div
                      role="progressbar"
                      aria-label={`${task.file.name} 上传进度`}
                      aria-valuemin={0}
                      aria-valuemax={100}
                      aria-valuenow={Math.floor(progress)}
                      className="h-1.5 overflow-hidden rounded-full bg-muted"
                    >
                      <div
                        className={cn(
                          "h-full rounded-full bg-primary transition-[width] duration-200",
                          task.status === "FAILED" && "bg-destructive",
                          task.status === "PAUSED" && "opacity-50",
                        )}
                        style={{ width: `${progress}%` }}
                      />
                    </div>
                    {task.error && task.status !== "CONFLICT" && (
                      <div className="flex items-start gap-1 text-xs break-words text-destructive">
                        <CircleAlert className="mt-0.5 size-3 shrink-0" />
                        <span>{task.error}</span>
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
            <div className="flex items-center justify-between border-t bg-muted/30 px-3 py-2">
              <span className="text-xs text-muted-foreground">单文件顺序上传，每块 5 MiB</span>
              <Button
                type="button"
                variant="ghost"
                size="xs"
                disabled={finishedCount === 0}
                onClick={clearFinished}
              >
                清除已结束
              </Button>
            </div>
          </>
        )}
      </aside>

      {conflictTask && (
        <ConflictDialog
          key={conflictTask.id}
          task={conflictTask}
          onResolve={(strategy, applyToFuture) =>
            resolveConflict(conflictTask.id, strategy, applyToFuture)
          }
          onCancel={() => cancel(conflictTask.id)}
        />
      )}
    </>
  );
}
