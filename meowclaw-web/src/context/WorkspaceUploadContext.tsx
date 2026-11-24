import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { toast } from "sonner";
import { WorkspaceUploadPanel } from "@/components/workspace/WorkspaceUploadPanel";
import {
  cancelWorkspaceUpload,
  completeWorkspaceUpload,
  createWorkspaceUploadSession,
  getWorkspaceUploadSession,
  uploadWorkspaceChunk,
} from "@/services/workspace";
import type {
  ConflictStrategy,
  WorkspaceUploadCompleteResult,
  WorkspaceUploadSession,
} from "@/types";

const CHUNK_SIZE = 5 * 1024 * 1024;
const MAX_CHUNK_RETRIES = 3;

export type WorkspaceUploadStatus =
  | "QUEUED"
  | "UPLOADING"
  | "PAUSED"
  | "CONFLICT"
  | "FAILED"
  | "COMPLETED";

export interface WorkspaceUploadTask {
  id: string;
  uploadId?: string;
  file: File;
  agentId: number;
  targetDir: string;
  status: WorkspaceUploadStatus;
  totalChunks: number;
  receivedChunks: number[];
  uploadedBytes: number;
  error?: string;
  skipped?: boolean;
  finalizing?: boolean;
}

export interface WorkspaceUploadContextValue {
  tasks: WorkspaceUploadTask[];
  enqueue: (files: File[] | FileList, agentId: number, targetDir: string) => void;
  pause: (id: string) => void;
  resume: (id: string) => void;
  cancel: (id: string) => void;
  dismiss: (id: string) => void;
  clearFinished: () => void;
  resolveConflict: (id: string, strategy: ConflictStrategy, applyToFuture: boolean) => void;
}

const WorkspaceUploadContext = createContext<WorkspaceUploadContextValue | null>(null);

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error && error.message ? error.message : fallback;
}

function isAbortError(error: unknown) {
  return error instanceof DOMException
    ? error.name === "AbortError"
    : typeof error === "object" && error !== null && "name" in error && error.name === "AbortError";
}

function isConflictError(error: unknown) {
  return /存在|同名|冲突|conflict|already exists/i.test(errorMessage(error, ""));
}

function normalizeReceivedChunks(chunks: number[], totalChunks: number) {
  if (!Array.isArray(chunks)) return [];
  return Array.from(
    new Set(chunks.filter((index) => Number.isInteger(index) && index >= 0 && index < totalChunks)),
  ).sort((a, b) => a - b);
}

function uploadedBytesFor(fileSize: number, chunks: number[]) {
  return chunks.reduce((bytes, index) => {
    const start = index * CHUNK_SIZE;
    return bytes + Math.max(0, Math.min(CHUNK_SIZE, fileSize - start));
  }, 0);
}

function createTaskId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) return crypto.randomUUID();
  return `upload-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export function WorkspaceUploadProvider({ children }: { children: ReactNode }) {
  const [tasks, setTasks] = useState<WorkspaceUploadTask[]>([]);
  const tasksRef = useRef<WorkspaceUploadTask[]>([]);
  const controllersRef = useRef(new Map<string, AbortController>());
  const runVersionsRef = useRef(new Map<string, number>());
  const conflictResolversRef = useRef(new Map<string, (strategy: ConflictStrategy | null) => void>());
  const futureConflictStrategyRef = useRef<ConflictStrategy | null>(null);
  const pumpingRef = useRef(false);

  const commitTasks = useCallback((nextTasks: WorkspaceUploadTask[]) => {
    tasksRef.current = nextTasks;
    setTasks(nextTasks);
  }, []);

  const patchTask = useCallback(
    (id: string, patch: Partial<WorkspaceUploadTask>) => {
      const current = tasksRef.current;
      if (!current.some((task) => task.id === id)) return;
      commitTasks(current.map((task) => (task.id === id ? { ...task, ...patch } : task)));
    },
    [commitTasks],
  );

  const bumpRunVersion = useCallback((id: string) => {
    const version = (runVersionsRef.current.get(id) ?? 0) + 1;
    runVersionsRef.current.set(id, version);
    return version;
  }, []);

  const isCurrentRun = useCallback((id: string, version: number) => {
    return (
      runVersionsRef.current.get(id) === version && tasksRef.current.some((task) => task.id === id)
    );
  }, []);

  const reconcileTask = useCallback(
    (task: WorkspaceUploadTask, session: WorkspaceUploadSession) => {
      const receivedChunks = normalizeReceivedChunks(session.receivedChunks, task.totalChunks);
      patchTask(task.id, {
        uploadId: session.uploadId,
        receivedChunks,
        uploadedBytes: uploadedBytesFor(task.file.size, receivedChunks),
      });
      return receivedChunks;
    },
    [patchTask],
  );

  const runTask = useCallback(
    async (id: string, version: number) => {
      const task = tasksRef.current.find((item) => item.id === id);
      if (!task || task.status !== "QUEUED") return;

      patchTask(id, { status: "UPLOADING", error: undefined, skipped: false, finalizing: false });

      try {
        const session = task.uploadId
          ? await getWorkspaceUploadSession(task.uploadId)
          : await createWorkspaceUploadSession({
              agentId: task.agentId,
              targetDir: task.targetDir,
              fileName: task.file.name,
              fileSize: task.file.size,
              chunkSize: CHUNK_SIZE,
              totalChunks: task.totalChunks,
            });

        // If cancellation happened while creating a session, clean up the newly created session.
        if (!tasksRef.current.some((item) => item.id === id)) {
          void cancelWorkspaceUpload(session.uploadId).catch(() => undefined);
          return;
        }

        // A paused task retains the latest authoritative server state for its next resume.
        let receivedChunks = reconcileTask(task, session);
        if (!isCurrentRun(id, version)) return;

        for (let index = 0; index < task.totalChunks; index += 1) {
          if (receivedChunks.includes(index)) continue;

          const controller = new AbortController();
          controllersRef.current.set(id, controller);
          let response: WorkspaceUploadSession | null = null;

          try {
            for (let attempt = 0; attempt <= MAX_CHUNK_RETRIES; attempt += 1) {
              try {
                const start = index * CHUNK_SIZE;
                const chunk = task.file.slice(start, Math.min(start + CHUNK_SIZE, task.file.size));
                response = await uploadWorkspaceChunk(
                  session.uploadId,
                  index,
                  chunk,
                  controller.signal,
                );
                break;
              } catch (error) {
                if (
                  !isCurrentRun(id, version) ||
                  controller.signal.aborted ||
                  isAbortError(error)
                ) {
                  return;
                }
                if (attempt === MAX_CHUNK_RETRIES) throw error;
              }
            }
          } finally {
            if (controllersRef.current.get(id) === controller) controllersRef.current.delete(id);
          }

          if (!response) return;

          // Only a successful server response advances chunk and byte progress.
          receivedChunks = reconcileTask(task, response);
          if (!isCurrentRun(id, version)) return;
        }

        let result: WorkspaceUploadCompleteResult;
        patchTask(id, { finalizing: true });
        try {
          result = await completeWorkspaceUpload(session.uploadId, "FAIL");
        } catch (error) {
          if (!isCurrentRun(id, version)) return;
          if (!isConflictError(error)) throw error;

          let strategy = futureConflictStrategyRef.current;
          if (!strategy) {
            patchTask(id, { status: "CONFLICT", error: errorMessage(error, "目标文件已存在"), finalizing: false });
            strategy = await new Promise<ConflictStrategy | null>((resolve) => {
              conflictResolversRef.current.set(id, resolve);
            });
            conflictResolversRef.current.delete(id);
            if (!strategy || !isCurrentRun(id, version)) return;
            patchTask(id, { status: "UPLOADING", error: undefined, finalizing: true });
          }

          result = await completeWorkspaceUpload(session.uploadId, strategy);
        }

        if (!isCurrentRun(id, version)) return;
        patchTask(id, {
          status: "COMPLETED",
          uploadedBytes: task.file.size,
          error: undefined,
          skipped: result.skipped,
          finalizing: false,
        });

        window.dispatchEvent(
          new CustomEvent("workspace-upload-completed", {
            detail: { agentId: task.agentId, targetDir: task.targetDir },
          }),
        );
        toast.success(result.skipped ? `${task.file.name} 已跳过` : `${task.file.name} 上传完成`);
      } catch (error) {
        if (!isCurrentRun(id, version) || isAbortError(error)) return;
        patchTask(id, {
          status: "FAILED",
          error: errorMessage(error, "上传失败，请稍后重试"),
          finalizing: false,
        });
      }
    },
    [isCurrentRun, patchTask, reconcileTask],
  );

  const pump = useCallback(async () => {
    if (pumpingRef.current) return;
    pumpingRef.current = true;
    try {
      while (true) {
        const next = tasksRef.current.find((task) => task.status === "QUEUED");
        if (!next) break;
        await runTask(next.id, bumpRunVersion(next.id));
      }
    } finally {
      pumpingRef.current = false;
    }
  }, [bumpRunVersion, runTask]);

  const enqueue = useCallback(
    (files: File[] | FileList, agentId: number, targetDir: string) => {
      const hasActiveTasks = tasksRef.current.some((task) =>
        task.status === "QUEUED" || task.status === "UPLOADING" || task.status === "PAUSED" || task.status === "CONFLICT");
      if (!hasActiveTasks) futureConflictStrategyRef.current = null;
      const newTasks = Array.from(files).map<WorkspaceUploadTask>((file) => ({
        id: createTaskId(),
        file,
        agentId,
        targetDir,
        status: "QUEUED",
        totalChunks: Math.ceil(file.size / CHUNK_SIZE),
        receivedChunks: [],
        uploadedBytes: 0,
        skipped: false,
      }));
      if (newTasks.length === 0) return;
      commitTasks([...tasksRef.current, ...newTasks]);
      void pump();
    },
    [commitTasks, pump],
  );

  const pause = useCallback(
    (id: string) => {
      const task = tasksRef.current.find((item) => item.id === id);
      if (!task || task.finalizing || (task.status !== "QUEUED" && task.status !== "UPLOADING")) return;
      bumpRunVersion(id);
      controllersRef.current.get(id)?.abort();
      controllersRef.current.delete(id);
      patchTask(id, { status: "PAUSED", error: undefined });
    },
    [bumpRunVersion, patchTask],
  );

  const resume = useCallback(
    (id: string) => {
      const task = tasksRef.current.find((item) => item.id === id);
      if (!task || (task.status !== "PAUSED" && task.status !== "FAILED")) return;
      patchTask(id, { status: "QUEUED", error: undefined });
      void pump();
    },
    [patchTask, pump],
  );

  const cancel = useCallback(
    (id: string) => {
      const task = tasksRef.current.find((item) => item.id === id);
      if (!task || task.finalizing || task.status === "COMPLETED") return;

      bumpRunVersion(id);
      controllersRef.current.get(id)?.abort();
      controllersRef.current.delete(id);
      const resolveConflict = conflictResolversRef.current.get(id);
      conflictResolversRef.current.delete(id);
      resolveConflict?.(null);
      commitTasks(tasksRef.current.filter((item) => item.id !== id));

      if (task.uploadId) void cancelWorkspaceUpload(task.uploadId).catch(() => undefined);
    },
    [bumpRunVersion, commitTasks],
  );

  const dismiss = useCallback(
    (id: string) => {
      const task = tasksRef.current.find((item) => item.id === id);
      if (!task || (task.status !== "COMPLETED" && task.status !== "FAILED")) return;
      if (task.status === "FAILED" && task.uploadId) {
        void cancelWorkspaceUpload(task.uploadId).catch(() => undefined);
      }
      commitTasks(tasksRef.current.filter((item) => item.id !== id));
    },
    [commitTasks],
  );

  const clearFinished = useCallback(() => {
    for (const task of tasksRef.current) {
      if (task.status === "FAILED" && task.uploadId) {
        void cancelWorkspaceUpload(task.uploadId).catch(() => undefined);
      }
    }
    commitTasks(
      tasksRef.current.filter((task) => task.status !== "COMPLETED" && task.status !== "FAILED"),
    );
  }, [commitTasks]);

  const resolveConflict = useCallback(
    (id: string, strategy: ConflictStrategy, applyToFuture: boolean) => {
      const task = tasksRef.current.find((item) => item.id === id);
      const resolve = conflictResolversRef.current.get(id);
      if (!task || task.status !== "CONFLICT" || !resolve) return;
      if (applyToFuture) futureConflictStrategyRef.current = strategy;
      conflictResolversRef.current.delete(id);
      patchTask(id, { status: "UPLOADING", error: undefined, finalizing: true });
      resolve(strategy);
    },
    [patchTask],
  );

  const hasBlockingTasks = tasks.some(
    (task) =>
      task.status === "QUEUED" ||
      task.status === "UPLOADING" ||
      task.status === "PAUSED" ||
      task.status === "CONFLICT",
  );

  useEffect(() => {
    if (!hasBlockingTasks) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [hasBlockingTasks]);

  const value = useMemo<WorkspaceUploadContextValue>(
    () => ({
      tasks,
      enqueue,
      pause,
      resume,
      cancel,
      dismiss,
      clearFinished,
      resolveConflict,
    }),
    [tasks, enqueue, pause, resume, cancel, dismiss, clearFinished, resolveConflict],
  );

  return (
    <WorkspaceUploadContext.Provider value={value}>
      {children}
      <WorkspaceUploadPanel />
    </WorkspaceUploadContext.Provider>
  );
}

// Context hooks intentionally share this module with their provider.
// eslint-disable-next-line react-refresh/only-export-components
export function useWorkspaceUpload() {
  const context = useContext(WorkspaceUploadContext);
  if (!context) throw new Error("useWorkspaceUpload 必须在 WorkspaceUploadProvider 内使用");
  return context;
}
