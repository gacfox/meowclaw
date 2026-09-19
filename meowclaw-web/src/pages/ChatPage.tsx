import { useState, useEffect, useCallback, useRef } from "react";
import { useSearchParams } from "react-router-dom";
import type { AgentDTO, ConversationDTO, ChatEventBatchDTO, ChatEventDTO, PageResult, LlmDTO } from "@/types";
import { listAgents } from "@/services/agent";
import { listLlms } from "@/services/llm";
import { listConversations, createConversation, getConversation, deleteConversation, renameConversation, listBatches, chatStream, watchStream, listRunningConversations, truncateAfterBatch, waitForTitle } from "@/services/conversation";
import { useAuthStore } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Plus, Send, Trash2, Loader2, ChevronRight, Copy, Pencil, RefreshCw, ArrowUp, ArrowDown, Check, Clock, TriangleAlert, X, ImagePlus } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { Alert, AlertTitle, AlertDescription, AlertAction } from "@/components/ui/alert";
import { motion, AnimatePresence } from "framer-motion";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { BatchBubble, FinalAnswerIndicator, ToolCallDetails } from "@/components/chat/ChatEventBubble";
import { toast } from "sonner";

interface StreamStep {
  type: "thinking" | "tool_call";
  content?: string;
  toolCallId?: string;
  name?: string;
  args?: string;
  result?: string;
}

function StreamBubble({ steps, content, thinking }: { steps: StreamStep[]; content: string; thinking?: boolean }) {
  return (
    <div className="max-w-[80%] space-y-2 rounded-lg bg-muted px-4 py-2 text-sm">
      {thinking && (
        <div className="flex items-center gap-2 text-muted-foreground">
          <Loader2 className="size-3 animate-spin" />
          思考中...
        </div>
      )}
      {steps.map((step, i) =>
        step.type === "thinking" ? (
          <details key={i} className="group text-xs text-muted-foreground" open>
            <summary className="flex cursor-pointer items-center gap-1 list-none [&::-webkit-details-marker]:hidden">
              <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
              思考过程
            </summary>
            <div className="mt-1 whitespace-pre-wrap">{step.content}</div>
          </details>
        ) : step.name === "final_answer" ? (
          <FinalAnswerIndicator key={i} />
        ) : (
          <ToolCallDetails
            key={i}
            name={step.name ?? "tool"}
            args={step.args}
            result={step.result}
          />
        )
      )}
      {content && <MarkdownRenderer content={content} />}
    </div>
  );
}

export function ChatPage() {
  const { user } = useAuthStore();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialHandledRef = useRef(false);
  const [agents, setAgents] = useState<AgentDTO[]>([]);
  const [selectedAgentId, setSelectedAgentId] = useState<number | null>(null);
  const [conversations, setConversations] = useState<ConversationDTO[]>([]);
  const [hasMoreConvos, setHasMoreConvos] = useState(true);
  const [convoPage, setConvoPage] = useState(1);
  const [loadingConvos, setLoadingConvos] = useState(false);
  const [loadingMoreConvos, setLoadingMoreConvos] = useState(false);
  const [selectedConvoId, setSelectedConvoId] = useState<number | null>(null);
  const [batches, setBatches] = useState<ChatEventBatchDTO[]>([]);
  const [loadingBatches, setLoadingBatches] = useState(false);

  const [input, setInput] = useState("");
  const [generatingTitleId, setGeneratingTitleId] = useState<number | null>(null);
  const [optimisticContent, setOptimisticContent] = useState<string | null>(null);
  const [optimisticImages, setOptimisticImages] = useState<string[]>([]);
  const [pendingImages, setPendingImages] = useState<{ dataUrl: string; name: string }[]>([]);
  const [llms, setLlms] = useState<LlmDTO[]>([]);
  const [streamContent, setStreamContent] = useState("");
  const [streamSteps, setStreamSteps] = useState<StreamStep[]>([]);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [streamConvoId, setStreamConvoId] = useState<number | null>(null);
  const [runningConvoIds, setRunningConvoIds] = useState<Set<number>>(new Set());
  const [contextStatusMap, setContextStatusMap] = useState<Record<number, "NORMAL" | "LOW" | "VERY_LOW">>({});
  const [dismissedStatusMap, setDismissedStatusMap] = useState<Record<number, boolean>>({});
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [editingBatchId, setEditingBatchId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState("");
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const convoListRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const editTextareaRef = useRef<HTMLTextAreaElement>(null);
  const selectedConvoIdRef = useRef<number | null>(null);
  const streamConvoIdRef = useRef<number | null>(null);

  useEffect(() => {
    selectedConvoIdRef.current = selectedConvoId;
  }, [selectedConvoId]);

  useEffect(() => {
    streamConvoIdRef.current = streamConvoId;
  }, [streamConvoId]);

  const currentConvo = conversations.find((c) => c.id === selectedConvoId);
  const currentAgent = currentConvo ? agents.find((a) => a.id === currentConvo.agentId) : null;
  const canVision = currentAgent
    ? (llms.find((l) => l.id === currentAgent.llmId)?.capabilities ?? "").split(",").map((s) => s.trim()).includes("vision")
    : false;
  const isCurrentConvoRunning = selectedConvoId != null
    && (streamConvoId === selectedConvoId || runningConvoIds.has(selectedConvoId));

  useEffect(() => {
    listAgents().then((list) => {
      setAgents(list);
      const agentIdParam = searchParams.get("agentId");
      const urlAgentId = agentIdParam ? Number(agentIdParam) : null;
      const validAgentId = urlAgentId && list.some((a) => a.id === urlAgentId) ? urlAgentId : list[0]?.id ?? null;
      setSelectedAgentId(validAgentId);
    });
    listLlms().then(setLlms).catch(() => {});
  }, []);

  const refreshRunningConversations = useCallback(async () => {
    try {
      const ids = await listRunningConversations();
      const merged = new Set(ids);
      const attached = streamConvoIdRef.current;
      if (attached != null) merged.add(attached);
      setRunningConvoIds(merged);
    } catch { /* 忽略轮询失败 */ }
  }, []);

  useEffect(() => {
    refreshRunningConversations();
  }, [refreshRunningConversations]);

  useEffect(() => {
    if (runningConvoIds.size === 0) return;
    const timer = setInterval(refreshRunningConversations, 3000);
    return () => clearInterval(timer);
  }, [runningConvoIds.size > 0, refreshRunningConversations]);

  const consumeEventStream = async (convoId: number, stream: ReadableStream<Uint8Array>) => {
    const reader = stream.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";
      for (const line of lines) {
        if (line.startsWith("data:")) {
          const jsonStr = line.slice(5).trim();
          if (!jsonStr || jsonStr === "[DONE]") continue;
          try {
            handleStreamEvent(convoId, JSON.parse(jsonStr) as ChatEventDTO);
          } catch { /* 跳过无法解析的行 */ }
        }
      }
    }
  };

  const finishStream = (convoId: number) => {
    if (streamConvoIdRef.current === convoId) {
      setStreamConvoId(null);
      setOptimisticContent(null);
      setOptimisticImages([]);
      setStreamContent("");
      setStreamSteps([]);
      setStreamError(null);
    }
    listBatches(convoId).then((newBatches) => {
      if (selectedConvoIdRef.current === convoId) {
        setBatches(newBatches);
      }
    });
    refreshRunningConversations();
    setGeneratingTitleId(convoId);
    waitForTitle(convoId).then((title) => {
      setGeneratingTitleId(null);
      if (title) {
        setConversations((prev) =>
          prev.map((c) => (c.id === convoId ? { ...c, title } : c))
        );
      }
    });
  };

  const attachWatch = async (convoId: number, runningBatch?: ChatEventBatchDTO) => {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setStreamConvoId(convoId);
    setStreamContent("");
    setStreamSteps([]);
    setStreamError(null);
    if (runningBatch) {
      setOptimisticContent(runningBatch.userContent);
      setOptimisticImages((runningBatch.attachments ?? []).map((a) => a.url));
    }
    try {
      const stream = await watchStream(convoId, controller.signal);
      await consumeEventStream(convoId, stream);
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === "AbortError") return;
    }
    if (streamConvoIdRef.current === convoId) {
      finishStream(convoId);
    }
  };

  useEffect(() => {
    const el = editTextareaRef.current;
    if (!el || editingBatchId === null) return;
    el.style.height = "auto";
    const lineHeight = parseFloat(getComputedStyle(el).lineHeight) || 20;
    const maxHeight = lineHeight * 5 + 4;
    const target = Math.min(el.scrollHeight, maxHeight);
    el.style.height = `${target}px`;
    el.style.overflowY = el.scrollHeight > maxHeight ? "auto" : "hidden";
  }, [editDraft, editingBatchId]);

  useEffect(() => {
    if (!selectedAgentId) return;
    setConversations([]);
    setConvoPage(1);
    setHasMoreConvos(true);
    setSelectedConvoId(null);
    setBatches([]);

    const handleUrlConversation = async () => {
      const convoIdParam = searchParams.get("conversationId");
      if (initialHandledRef.current || !convoIdParam) {
        await loadConversations(selectedAgentId, 1, true);
        initialHandledRef.current = true;
        return;
      }
      const urlConvoId = Number(convoIdParam);
      const result = await loadConversations(selectedAgentId, 1, true);
      if (result.list.some((c) => c.id === urlConvoId)) {
        setSelectedConvoId(urlConvoId);
      } else {
        try {
          const convo = await getConversation(urlConvoId);
          if (convo.agentId === selectedAgentId) {
            setConversations((prev) => [convo, ...prev]);
            setSelectedConvoId(convo.id);
          }
        } catch {
          // ignore
        }
      }
      initialHandledRef.current = true;
      setSearchParams({}, { replace: true });
    };
    handleUrlConversation();
  }, [selectedAgentId]);

  useEffect(() => {
    abortRef.current?.abort();
    setStreamConvoId(null);
    setOptimisticContent(null);
    setOptimisticImages([]);
    setStreamContent("");
    setStreamSteps([]);
    setStreamError(null);
    if (!selectedConvoId) {
      setBatches([]);
      return;
    }
    const convoId = selectedConvoId;
    setLoadingBatches(true);
    listBatches(convoId)
      .then((loaded) => {
        setBatches(loaded);
        const runningBatch = loaded.find((b) => b.status === "RUNNING");
        if (runningBatch) {
          setRunningConvoIds((prev) => new Set(prev).add(convoId));
          attachWatch(convoId, runningBatch);
        }
      })
      .finally(() => setLoadingBatches(false));
  }, [selectedConvoId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [batches, streamContent, streamSteps]);

  const loadConversations = useCallback(async (agentId: number, page: number, reset: boolean): Promise<PageResult<ConversationDTO>> => {
    if (reset) {
      setLoadingConvos(true);
    } else {
      setLoadingMoreConvos(true);
    }
    try {
      const result = await listConversations(agentId, page, 20, "CHAT");
      if (reset) {
        setConversations(result.list);
      } else {
        setConversations((prev) => {
          const existingIds = new Set(prev.map((c) => c.id));
          return [...prev, ...result.list.filter((c) => !existingIds.has(c.id))];
        });
      }
      setHasMoreConvos(result.list.length >= 20);
      setConvoPage(page);
      return result;
    } finally {
      setLoadingConvos(false);
      setLoadingMoreConvos(false);
    }
  }, []);

  const handleConvoScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 20;
    if (atBottom && hasMoreConvos && !loadingConvos && !loadingMoreConvos && selectedAgentId) {
      loadConversations(selectedAgentId, convoPage + 1, false);
    }
  }, [hasMoreConvos, loadingConvos, loadingMoreConvos, selectedAgentId, convoPage, loadConversations]);

  const handleNewConvo = async () => {
    if (!selectedAgentId) return;
    const convo = await createConversation(selectedAgentId);
    setConversations((prev) => [convo, ...prev]);
    setSelectedConvoId(convo.id);
    convoListRef.current?.scrollTo({ top: 0 });
  };

  const confirmDelete = async () => {
    if (deleteTargetId == null) return;
    const id = deleteTargetId;
    setDeleteTargetId(null);
    await deleteConversation(id);
    setConversations((prev) => prev.filter((c) => c.id !== id));
    if (selectedConvoId === id) {
      setSelectedConvoId(null);
      setBatches([]);
    }
  };

  const startRename = (convo: ConversationDTO) => {
    setRenamingId(convo.id);
    setRenameDraft(convo.title ?? "");
  };

  const cancelRename = () => {
    setRenamingId(null);
    setRenameDraft("");
  };

  const commitRename = async () => {
    const id = renamingId;
    const title = renameDraft.trim();
    setRenamingId(null);
    setRenameDraft("");
    if (id == null || !title) return;
    try {
      const updated = await renameConversation(id, title);
      setConversations((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "重命名失败");
    }
  };

  const handleCopy = async (text: string, id: string) => {
    await navigator.clipboard.writeText(text);
    setCopiedId(id);
    setTimeout(() => setCopiedId(null), 2000);
  };

  const handleEdit = (batch: ChatEventBatchDTO) => {
    if (isCurrentConvoRunning) return;
    setEditingBatchId(batch.id);
    setEditDraft(batch.userContent);
  };

  const confirmEdit = async () => {
    if (!selectedConvoId || !editingBatchId || !editDraft.trim() || isCurrentConvoRunning) return;
    const content = editDraft.trim();
    const batchId = editingBatchId;
    setEditingBatchId(null);
    abortRef.current?.abort();
    await truncateAfterBatch(selectedConvoId, batchId, true);
    setBatches(await listBatches(selectedConvoId));
    triggerChat(content);
  };

  const cancelEdit = () => {
    setEditingBatchId(null);
    setEditDraft("");
  };

  const handleRegenerate = async (batch: ChatEventBatchDTO) => {
    if (!selectedConvoId || isCurrentConvoRunning) return;
    const content = batch.userContent;
    abortRef.current?.abort();
    await truncateAfterBatch(selectedConvoId, batch.id, true);
    setBatches(await listBatches(selectedConvoId));
    triggerChat(content);
  };

  const triggerChat = async (content: string, images: string[] = []) => {
    if (!selectedConvoId || streamConvoId === selectedConvoId || runningConvoIds.has(selectedConvoId)) return;
    const chatConvoId = selectedConvoId;
    setStreamConvoId(chatConvoId);
    setOptimisticContent(content);
    setOptimisticImages(images);
    setStreamContent("");
    setStreamSteps([]);
    setStreamError(null);
    setRunningConvoIds((prev) => new Set(prev).add(chatConvoId));

    try {
      const controller = new AbortController();
      abortRef.current = controller;
      const stream = await chatStream(chatConvoId, content, images, controller.signal);
      await consumeEventStream(chatConvoId, stream);
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      setStreamError(e instanceof Error ? e.message : "发送失败");
    }
    if (streamConvoIdRef.current !== chatConvoId) return;
    finishStream(chatConvoId);
  };

  const MAX_PENDING_IMAGES = 5;

  const handleImageSelect = async (files: FileList | null) => {
    if (!files || files.length === 0) return;
    const remaining = MAX_PENDING_IMAGES - pendingImages.length;
    if (remaining <= 0) {
      toast.error(`最多上传 ${MAX_PENDING_IMAGES} 张图片`);
      return;
    }
    const selected = Array.from(files).slice(0, remaining);
    if (files.length > remaining) {
      toast.error(`最多上传 ${MAX_PENDING_IMAGES} 张图片`);
    }
    for (const file of selected) {
      if (file.size > 10 * 1024 * 1024) {
        toast.error(`图片 ${file.name} 超过 10MB`);
        continue;
      }
      try {
        const dataUrl = await new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(reader.result as string);
          reader.onerror = () => reject(new Error("读取图片失败"));
          reader.readAsDataURL(file);
        });
        setPendingImages((prev) => [...prev, { dataUrl, name: file.name }]);
      } catch {
        toast.error(`读取图片 ${file.name} 失败`);
      }
    }
  };

  const handleSend = () => {
    if ((!input.trim() && pendingImages.length === 0) || !selectedConvoId || isCurrentConvoRunning) return;
    const content = input.trim();
    const images = pendingImages.map((p) => p.dataUrl);
    setInput("");
    setPendingImages([]);
    triggerChat(content, images);
  };

  const handleStreamEvent = (convoId: number, event: ChatEventDTO) => {
    switch (event.type) {
      case "thinking":
        if (event.content) setStreamSteps((prev) => [...prev, { type: "thinking", content: event.content! }]);
        break;
      case "thinking_delta":
        setStreamSteps((prev) => {
          const last = prev[prev.length - 1];
          if (last && last.type === "thinking") {
            const updated = [...prev];
            updated[updated.length - 1] = { ...last, content: (last.content ?? "") + (event.content ?? "") };
            return updated;
          }
          return [...prev, { type: "thinking", content: event.content ?? "" }];
        });
        break;
      case "final_answer_delta":
        setStreamContent((prev) => prev + (event.content ?? ""));
        break;
      case "tool_call":
        setStreamSteps((prev) => [...prev, {
          type: "tool_call",
          toolCallId: event.toolCallId ?? undefined,
          name: event.toolName ?? "",
          args: event.toolArguments ?? "",
        }]);
        break;
      case "tool_result":
        setStreamSteps((prev) => {
          const updated = [...prev];
          const match = updated.find(
            (s) => s.type === "tool_call" && s.toolCallId === event.toolCallId && s.result === undefined
          );
          if (match) {
            match.result = event.content ?? "";
          }
          return updated;
        });
        break;
      case "final_answer":
        setStreamContent((prev) => prev + (event.content ?? ""));
        break;
      case "error":
        setStreamError(event.content ?? "未知错误");
        break;
      case "context_status":
        if (event.content === "NORMAL" || event.content === "LOW" || event.content === "VERY_LOW") {
          const status = event.content;
          setContextStatusMap((prev) => ({
            ...prev,
            [convoId]: status,
          }));
          setDismissedStatusMap((prev) => ({
            ...prev,
            [convoId]: false,
          }));
        }
        break;
      case "context_compression":
        break;
    }
  };

  return (
    <div className="flex h-[calc(100vh-3.5rem)] -m-6">
      {/* Left Panel */}
      <div className="flex w-72 shrink-0 flex-col border-r bg-muted/30">
        <div className="border-b p-3">
          <Select value={selectedAgentId != null ? String(selectedAgentId) : ""} onValueChange={(v) => setSelectedAgentId(Number(v))}>
            <SelectTrigger className="w-full">
              <SelectValue placeholder="选择智能体" />
            </SelectTrigger>
            <SelectContent>
              {agents.map((a) => (
                <SelectItem key={a.id} value={String(a.id)}>{a.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="border-b p-3">
          <Button className="w-full" size="sm" onClick={handleNewConvo} disabled={!selectedAgentId}>
            <Plus className="mr-1 size-4" />
            新对话
          </Button>
        </div>

        <div className="flex-1 overflow-y-auto" ref={convoListRef} onScroll={handleConvoScroll}>
          {conversations.map((convo) => {
            const convoBusy = runningConvoIds.has(convo.id) || (!convo.title && generatingTitleId === convo.id);
            const renaming = renamingId === convo.id;
            return (
            <div
              key={convo.id}
              className={`group flex cursor-pointer items-center gap-2 px-3 py-2 text-sm hover:bg-muted ${selectedConvoId === convo.id ? "bg-muted" : ""}`}
              onClick={() => { if (!renaming) setSelectedConvoId(convo.id); }}
            >
              {renaming ? (
                <input
                  autoFocus
                  value={renameDraft}
                  onChange={(e) => setRenameDraft(e.target.value)}
                  onClick={(e) => e.stopPropagation()}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") commitRename();
                    if (e.key === "Escape") cancelRename();
                  }}
                  onBlur={commitRename}
                  className="flex-1 rounded bg-background px-1.5 py-0.5 text-sm outline-none ring-1 ring-ring"
                />
              ) : (
                <>
                  <span className="flex-1 truncate">{convo.title ?? "新对话"}</span>
                  {convoBusy && <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />}
                  {!convoBusy && (
                    <Button
                      variant="ghost"
                      size="icon-xs"
                      className="opacity-0 group-hover:opacity-100"
                      onClick={(e) => { e.stopPropagation(); startRename(convo); }}
                      title="重命名"
                    >
                      <Pencil className="size-3" />
                    </Button>
                  )}
                  <Button
                    variant="ghost"
                    size="icon-xs"
                    className="opacity-0 group-hover:opacity-100"
                    onClick={(e) => { e.stopPropagation(); setDeleteTargetId(convo.id); }}
                  >
                    <Trash2 className="size-3 text-destructive" />
                  </Button>
                </>
              )}
            </div>
            );
          })}
          {loadingMoreConvos && <div className="py-2 text-center text-xs text-muted-foreground">加载中...</div>}
          {loadingConvos && conversations.length === 0 && <div className="py-2 text-center text-xs text-muted-foreground">加载中...</div>}
        </div>
      </div>

      {/* Right Panel - Chat Area */}
      <div className="flex flex-1 flex-col">
        {selectedConvoId ? (
          <>
            <div className="flex-1 overflow-y-auto p-4">
              {loadingBatches ? (
                <div className="flex h-full items-center justify-center text-muted-foreground">加载中...</div>
              ) : (
                <div className="mx-auto max-w-3xl space-y-4">
                  {(() => {
                    const status = selectedConvoId ? contextStatusMap[selectedConvoId] : undefined;
                    const showAlert = status && status !== "NORMAL" && selectedConvoId && !dismissedStatusMap[selectedConvoId];
                    return (
                      <AnimatePresence>
                        {showAlert && (
                          <motion.div
                            key={`context-alert-${selectedConvoId}`}
                            initial={{ opacity: 0, y: -12 }}
                            animate={{ opacity: 1, y: 0 }}
                            exit={{ opacity: 0, y: -12 }}
                            className="sticky top-0 z-10 mb-2"
                          >
                            <Alert
                              variant={status === "VERY_LOW" ? "destructive" : "default"}
                              className={status === "LOW" ? "border-amber-500/50 text-amber-700 dark:text-amber-400 [&>svg]:text-amber-500" : undefined}
                            >
                              <TriangleAlert className="size-4" />
                              <AlertTitle>{status === "VERY_LOW" ? "上下文空间严重不足" : "上下文空间不足"}</AlertTitle>
                              <AlertDescription>注意：当上下文空间使用超过93%时，系统将自动进行上下文压缩。</AlertDescription>
                              <AlertAction>
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-6 w-6"
                                  onClick={() => setDismissedStatusMap((prev) => ({ ...prev, [selectedConvoId!]: true }))}
                                >
                                  <X className="size-4" />
                                </Button>
                              </AlertAction>
                            </Alert>
                          </motion.div>
                        )}
                      </AnimatePresence>
                    );
                  })()}
                  {batches.filter((batch) => !(streamConvoId === selectedConvoId && batch.status === "RUNNING")).map((batch) => {
                    if (batch.type === "CONTEXT_COMPACTION") {
                      const notice = batch.events.find((event) => event.type === "context_compression")?.content;
                      return (
                        <div key={batch.id} className="flex justify-center">
                          <div className="rounded-full bg-muted px-4 py-1.5 text-xs text-muted-foreground">
                            {notice ?? "系统已进行主动上下文压缩"}
                          </div>
                        </div>
                      );
                    }
                    const finalAnswer = batch.events.find((e) => e.type === "final_answer");
                    return (
                    <div key={batch.id} className="space-y-2">
                      {/* User bubble */}
                      <div className="flex flex-col items-end">
                        <div className={`flex items-start justify-end gap-2 ${editingBatchId === batch.id ? "w-full" : ""}`}>
                          <div className={`flex ${editingBatchId === batch.id ? "w-[80%]" : "max-w-[80%]"} flex-col`}>
                            {editingBatchId === batch.id ? (
                              <div className="rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground">
                                <textarea
                                  ref={editTextareaRef}
                                  value={editDraft}
                                  onChange={(e) => setEditDraft(e.target.value)}
                                  className="w-full resize-none bg-transparent text-sm outline-none"
                                  rows={1}
                                  autoFocus
                                  onKeyDown={(e) => {
                                    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); confirmEdit(); }
                                    if (e.key === "Escape") cancelEdit();
                                  }}
                                />
                                <div className="mt-2 flex justify-end gap-1 border-t border-primary-foreground/20 pt-2">
                                  <Button variant="ghost" size="sm" className="h-6 text-xs text-primary-foreground hover:bg-primary-foreground/20" onClick={cancelEdit}>取消</Button>
                                  <Button size="sm" className="h-6 bg-primary-foreground text-xs text-primary hover:bg-primary-foreground/90" onClick={confirmEdit}>发送</Button>
                                </div>
                              </div>
                            ) : (
                              <div className="rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground">
                                {batch.attachments && batch.attachments.length > 0 && (
                                  <div className="mb-2 flex flex-wrap gap-2">
                                    {batch.attachments.map((att) => (
                                      <a key={att.name} href={att.url} target="_blank" rel="noreferrer">
                                        <img src={att.url} alt={att.name} className="max-h-40 rounded" />
                                      </a>
                                    ))}
                                  </div>
                                )}
                                <div className="whitespace-pre-wrap">{batch.userContent}</div>
                              </div>
                            )}
                            {editingBatchId !== batch.id && (
                              <div className="flex items-center gap-1 pt-1">
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Button variant="ghost" size="icon-xs" onClick={() => handleCopy(batch.userContent, `user-${batch.id}`)}>
                                      {copiedId === `user-${batch.id}` ? <Check className="size-3 text-green-500" /> : <Copy className="size-3" />}
                                    </Button>
                                  </TooltipTrigger>
                                  <TooltipContent>复制</TooltipContent>
                                </Tooltip>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Button variant="ghost" size="icon-xs" onClick={() => handleEdit(batch)}>
                                      <Pencil className="size-3" />
                                    </Button>
                                  </TooltipTrigger>
                                  <TooltipContent>编辑</TooltipContent>
                                </Tooltip>
                              </div>
                            )}
                          </div>
                          <Avatar className="mt-0.5 size-7 shrink-0">
                            <AvatarImage src={user?.avatarUrl ?? undefined} />
                            <AvatarFallback className="text-xs">{user?.displayName?.[0]?.toUpperCase() ?? "U"}</AvatarFallback>
                          </Avatar>
                        </div>
                      </div>
                      {/* Agent response bubble */}
                      {batch.events.length > 0 && (
                        <div className="flex flex-col items-start">
                          <div className="flex items-start justify-start gap-2">
                            <Avatar className="mt-0.5 size-7 shrink-0">
                              <AvatarImage src={currentAgent?.avatarUrl ?? undefined} />
                              <AvatarFallback className="text-xs">{currentAgent?.name?.[0]?.toUpperCase() ?? "A"}</AvatarFallback>
                            </Avatar>
                            <BatchBubble events={batch.events} />
                          </div>
                          <div className="ml-9 flex items-center gap-1">
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="ghost" size="icon-xs" onClick={() => finalAnswer?.content && handleCopy(finalAnswer.content, `agent-${batch.id}`)}>
                                  {copiedId === `agent-${batch.id}` ? <Check className="size-3 text-green-500" /> : <Copy className="size-3" />}
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>复制</TooltipContent>
                            </Tooltip>
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button variant="ghost" size="icon-xs" onClick={() => handleRegenerate(batch)} disabled={isCurrentConvoRunning}>
                                  <RefreshCw className="size-3" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>重新生成</TooltipContent>
                            </Tooltip>
                            {batch.inputTokens != null && batch.outputTokens != null && (
                              <span className="ml-2 flex items-center gap-2 text-xs text-muted-foreground">
                                <span className="inline-flex items-center gap-0.5"><ArrowUp className="size-3" />{batch.inputTokens}</span>
                                <span className="inline-flex items-center gap-0.5"><ArrowDown className="size-3" />{batch.outputTokens}</span>
                              </span>
                            )}
                            {batch.completedAt && (() => {
                              const d = new Date(batch.completedAt);
                              const pad = (n: number) => String(n).padStart(2, "0");
                              return (
                              <span className="inline-flex items-center gap-0.5 text-xs text-muted-foreground">
                                <Clock className="size-3 ml-2" />
                                {((batch.completedAt - batch.createdAt) / 1000).toFixed(1)}s
                                <span className="ml-1">{d.getFullYear()}-{pad(d.getMonth() + 1)}-{pad(d.getDate())} {pad(d.getHours())}:{pad(d.getMinutes())}:{pad(d.getSeconds())}</span>
                              </span>
                              );
                            })()}
                          </div>
                        </div>
                      )}
                      {batch.status === "ERROR" && batch.errorMessage && (
                        <div className="flex items-start justify-start gap-2">
                          <Avatar className="mt-0.5 size-7 shrink-0">
                            <AvatarImage src={currentAgent?.avatarUrl ?? undefined} />
                            <AvatarFallback className="text-xs">{currentAgent?.name?.[0]?.toUpperCase() ?? "A"}</AvatarFallback>
                          </Avatar>
                          <div className="text-destructive text-sm">{batch.errorMessage}</div>
                        </div>
                      )}
                    </div>
                  );
                  })}

                  {/* Optimistic user bubble during streaming */}
                  {(optimisticContent !== null || optimisticImages.length > 0) && streamConvoId === selectedConvoId && (
                    <div className="flex items-start justify-end gap-2">
                      <div className="max-w-[80%] rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground">
                        {optimisticImages.length > 0 && (
                          <div className="mb-2 flex flex-wrap gap-2">
                            {optimisticImages.map((dataUrl, i) => (
                              <img key={i} src={dataUrl} alt="" className="max-h-40 rounded" />
                            ))}
                          </div>
                        )}
                        {optimisticContent && <div className="whitespace-pre-wrap">{optimisticContent}</div>}
                      </div>
                      <Avatar className="mt-0.5 size-7 shrink-0">
                        <AvatarImage src={user?.avatarUrl ?? undefined} />
                        <AvatarFallback className="text-xs">{user?.displayName?.[0]?.toUpperCase() ?? "U"}</AvatarFallback>
                      </Avatar>
                    </div>
                  )}

                  {/* Streaming agent response */}
                  {streamConvoId === selectedConvoId && (
                    <div className="flex items-start justify-start gap-2">
                      <Avatar className="mt-0.5 size-7 shrink-0">
                        <AvatarImage src={currentAgent?.avatarUrl ?? undefined} />
                        <AvatarFallback className="text-xs">{currentAgent?.name?.[0]?.toUpperCase() ?? "A"}</AvatarFallback>
                      </Avatar>
                      <StreamBubble steps={streamSteps} content={streamContent} thinking={!streamContent && !streamError && streamSteps.length === 0} />
                      {streamError && <div className="text-destructive">{streamError}</div>}
                    </div>
                  )}

                  <div ref={messagesEndRef} />
                </div>
              )}
            </div>

            <div className="border-t p-4">
              <div className="mx-auto max-w-3xl">
                {pendingImages.length > 0 && (
                  <div className="mb-2 flex flex-wrap gap-2">
                    {pendingImages.map((img, i) => (
                      <div key={i} className="relative">
                        <img src={img.dataUrl} alt={img.name} className="size-14 rounded border object-cover" />
                        <button
                          className="absolute -right-1.5 -top-1.5 rounded-full bg-destructive p-0.5 text-destructive-foreground"
                          onClick={() => setPendingImages((prev) => prev.filter((_, idx) => idx !== i))}
                          title="移除"
                        >
                          <X className="size-3" />
                        </button>
                      </div>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg,image/webp,image/gif"
                    multiple
                    className="hidden"
                    onChange={(e) => { handleImageSelect(e.target.files); e.target.value = ""; }}
                  />
                  <Tooltip>
                    <TooltipTrigger asChild>
                      <span>
                        <Button
                          variant="outline"
                          size="icon"
                          disabled={!canVision || isCurrentConvoRunning}
                          onClick={() => fileInputRef.current?.click()}
                        >
                          <ImagePlus className="size-4" />
                        </Button>
                      </span>
                    </TooltipTrigger>
                    <TooltipContent>{canVision ? "上传图片" : "当前模型不支持图片输入"}</TooltipContent>
                  </Tooltip>
                  <Input
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
                    placeholder={isCurrentConvoRunning ? "正在执行中..." : "输入消息..."}
                    disabled={isCurrentConvoRunning}
                    className="flex-1"
                  />
                  <Button onClick={handleSend} disabled={isCurrentConvoRunning || (!input.trim() && pendingImages.length === 0)}>
                    {isCurrentConvoRunning ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
                  </Button>
                </div>
              </div>
            </div>
          </>
        ) : (
          <div className="flex flex-1 items-center justify-center text-muted-foreground">
            {selectedAgentId ? "点击「新对话」开始聊天" : "请先选择一个智能体"}
          </div>
        )}
      </div>

      <AlertDialog open={deleteTargetId != null} onOpenChange={(open) => { if (!open) setDeleteTargetId(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>删除会话</AlertDialogTitle>
            <AlertDialogDescription>确定要删除该会话吗？删除后无法恢复。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
