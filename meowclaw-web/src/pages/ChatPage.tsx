import { useState, useEffect, useCallback, useRef } from "react";
import type { AgentDTO, ConversationDTO, ChatEventBatchDTO, ChatEventDTO } from "@/types";
import { listAgents } from "@/services/agent";
import { listConversations, createConversation, getConversation, deleteConversation, renameConversation, listBatches, chatStream, truncateAfterBatch, waitForTitle } from "@/services/conversation";
import { useAuthStore } from "@/stores/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Plus, Send, Trash2, Loader2, Wrench, ChevronRight, Copy, Pencil, RefreshCw, ArrowUp, ArrowDown, Check, Clock } from "lucide-react";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { MarkdownRenderer } from "@/components/markdown/MarkdownRenderer";
import { toast } from "sonner";

interface StreamStep {
  type: "thinking" | "tool_call";
  content?: string;
  toolCallId?: string;
  name?: string;
  args?: string;
  result?: string;
}

function BatchBubble({ events }: { events: ChatEventDTO[] }) {
  const toolResults = new Map<string, string>();
  for (const e of events) {
    if (e.type === "tool_result" && e.toolCallId) {
      toolResults.set(e.toolCallId, e.content ?? "");
    }
  }
  const finalAnswer = events.find((e) => e.type === "final_answer");

  return (
    <div className="max-w-[80%] space-y-2 rounded-lg bg-muted px-4 py-2 text-sm">
      {events.map((event, i) => {
        if (event.type === "thinking") {
          return (
            <details key={i} className="group text-xs text-muted-foreground">
              <summary className="flex cursor-pointer items-center gap-1 list-none [&::-webkit-details-marker]:hidden">
                <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
                思考过程
              </summary>
              <div className="mt-1 whitespace-pre-wrap">{event.content}</div>
            </details>
          );
        }
        if (event.type === "tool_call") {
          const result = event.toolCallId ? toolResults.get(event.toolCallId) : undefined;
          return (
            <details key={i} className="group text-xs">
              <summary className="flex cursor-pointer items-center gap-1 text-muted-foreground list-none [&::-webkit-details-marker]:hidden">
                <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
                <Wrench className="size-3" />
                {event.toolName}
              </summary>
              <div className="mt-1 rounded bg-background/50 p-2">
                <pre className="whitespace-pre-wrap text-xs">{result ?? "(无结果)"}</pre>
              </div>
            </details>
          );
        }
        if (event.type === "error") {
          return <div key={i} className="text-destructive">{event.content}</div>;
        }
        return null;
      })}
      {finalAnswer?.content && <MarkdownRenderer content={finalAnswer.content} />}
    </div>
  );
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
          <details key={i} className="group text-xs text-muted-foreground">
            <summary className="flex cursor-pointer items-center gap-1 list-none [&::-webkit-details-marker]:hidden">
              <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
              思考过程
            </summary>
            <div className="mt-1 whitespace-pre-wrap">{step.content}</div>
          </details>
        ) : (
          <details key={i} className="group text-xs">
            <summary className="flex cursor-pointer items-center gap-1 text-muted-foreground list-none [&::-webkit-details-marker]:hidden">
              <ChevronRight className="size-3 shrink-0 transition-transform group-open:rotate-90" />
              <Wrench className="size-3" />
              {step.name}
            </summary>
            <div className="mt-1 rounded bg-background/50 p-2">
              <pre className="whitespace-pre-wrap text-xs">{step.result ?? "(无结果)"}</pre>
            </div>
          </details>
        )
      )}
      {content && <MarkdownRenderer content={content} />}
    </div>
  );
}

export function ChatPage() {
  const { user } = useAuthStore();
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
  const [sending, setSending] = useState(false);
  const [generatingTitleId, setGeneratingTitleId] = useState<number | null>(null);
  const [optimisticContent, setOptimisticContent] = useState<string | null>(null);
  const [streamContent, setStreamContent] = useState("");
  const [streamSteps, setStreamSteps] = useState<StreamStep[]>([]);
  const [streamError, setStreamError] = useState<string | null>(null);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [editingBatchId, setEditingBatchId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState("");
  const [renamingId, setRenamingId] = useState<number | null>(null);
  const [renameDraft, setRenameDraft] = useState("");

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const convoListRef = useRef<HTMLDivElement>(null);
  const abortRef = useRef<AbortController | null>(null);

  const currentConvo = conversations.find((c) => c.id === selectedConvoId);
  const currentAgent = currentConvo ? agents.find((a) => a.id === currentConvo.agentId) : null;

  useEffect(() => {
    listAgents().then((list) => {
      setAgents(list);
      if (list.length > 0 && !selectedAgentId) {
        setSelectedAgentId(list[0].id);
      }
    });
  }, []);

  useEffect(() => {
    if (!selectedAgentId) return;
    setConversations([]);
    setConvoPage(1);
    setHasMoreConvos(true);
    setSelectedConvoId(null);
    setBatches([]);
    loadConversations(selectedAgentId, 1, true);
  }, [selectedAgentId]);

  useEffect(() => {
    if (!selectedConvoId) {
      setBatches([]);
      return;
    }
    setLoadingBatches(true);
    listBatches(selectedConvoId)
      .then(setBatches)
      .finally(() => setLoadingBatches(false));
  }, [selectedConvoId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [batches, streamContent, streamSteps]);

  const loadConversations = useCallback(async (agentId: number, page: number, reset: boolean) => {
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
        setConversations((prev) => [...prev, ...result.list]);
      }
      setHasMoreConvos(result.list.length >= 20);
      setConvoPage(page);
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
    if (sending) return;
    setEditingBatchId(batch.id);
    setEditDraft(batch.userContent);
  };

  const confirmEdit = async () => {
    if (!selectedConvoId || !editingBatchId || !editDraft.trim()) return;
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
    if (!selectedConvoId || sending) return;
    const content = batch.userContent;
    abortRef.current?.abort();
    await truncateAfterBatch(selectedConvoId, batch.id, true);
    setBatches(await listBatches(selectedConvoId));
    triggerChat(content);
  };

  const triggerChat = async (content: string) => {
    if (!selectedConvoId || sending) return;
    setSending(true);
    setOptimisticContent(content);
    setStreamContent("");
    setStreamSteps([]);
    setStreamError(null);

    try {
      const controller = new AbortController();
      abortRef.current = controller;
      const stream = await chatStream(selectedConvoId, content, controller.signal);

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
              const event: ChatEventDTO = JSON.parse(jsonStr);
              handleStreamEvent(event);
            } catch { /* skip malformed */ }
          }
        }
      }
    } catch (e: unknown) {
      if (e instanceof DOMException && e.name === "AbortError") return;
      setStreamError(e instanceof Error ? e.message : "发送失败");
    } finally {
      setSending(false);
      if (selectedConvoId) {
        const convoId = selectedConvoId;
        listBatches(convoId).then((newBatches) => {
          setBatches(newBatches);
          setOptimisticContent(null);
          setStreamContent("");
          setStreamSteps([]);
          setStreamError(null);
        });
        const wasFirstBatch = !currentConvo?.title;
        if (wasFirstBatch) {
          setGeneratingTitleId(convoId);
          waitForTitle(convoId).then((title) => {
            setGeneratingTitleId(null);
            if (title) {
              setConversations((prev) =>
                prev.map((c) => (c.id === convoId ? { ...c, title } : c))
              );
            }
          });
        } else {
          getConversation(convoId).then((updated) => {
            setConversations((prev) =>
              prev.map((c) => (c.id === convoId ? updated : c))
            );
          });
        }
      }
    }
  };

  const handleSend = () => {
    if (!input.trim() || !selectedConvoId || sending) return;
    const content = input.trim();
    setInput("");
    triggerChat(content);
  };

  const handleStreamEvent = (event: ChatEventDTO) => {
    switch (event.type) {
      case "thinking":
        if (event.content) setStreamSteps((prev) => [...prev, { type: "thinking", content: event.content! }]);
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
            const titleBusy = !convo.title && ((sending && selectedConvoId === convo.id) || generatingTitleId === convo.id);
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
                  {titleBusy && <Loader2 className="size-3 shrink-0 animate-spin text-muted-foreground" />}
                  {!titleBusy && (
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
                  {batches.map((batch) => {
                    const finalAnswer = batch.events.find((e) => e.type === "final_answer");
                    return (
                    <div key={batch.id} className="space-y-2">
                      {/* User bubble */}
                      <div className="flex flex-col items-end">
                        <div className="flex items-start justify-end gap-2">
                          <div className="flex max-w-[80%] flex-col">
                            {editingBatchId === batch.id ? (
                              <div className="rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground">
                                <textarea
                                  value={editDraft}
                                  onChange={(e) => setEditDraft(e.target.value)}
                                  className="w-full resize-none bg-transparent text-sm outline-none"
                                  rows={Math.min(editDraft.split("\n").length, 10)}
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
                                <Button variant="ghost" size="icon-xs" onClick={() => handleRegenerate(batch)} disabled={sending}>
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
                  {optimisticContent && (
                    <div className="flex items-start justify-end gap-2">
                      <div className="max-w-[80%] rounded-lg bg-primary px-4 py-2 text-sm text-primary-foreground">
                        <div className="whitespace-pre-wrap">{optimisticContent}</div>
                      </div>
                      <Avatar className="mt-0.5 size-7 shrink-0">
                        <AvatarImage src={user?.avatarUrl ?? undefined} />
                        <AvatarFallback className="text-xs">{user?.displayName?.[0]?.toUpperCase() ?? "U"}</AvatarFallback>
                      </Avatar>
                    </div>
                  )}

                  {/* Streaming agent response */}
                  {(sending || streamContent || streamError || streamSteps.length > 0) && (
                    <div className="flex items-start justify-start gap-2">
                      <Avatar className="mt-0.5 size-7 shrink-0">
                        <AvatarImage src={currentAgent?.avatarUrl ?? undefined} />
                        <AvatarFallback className="text-xs">{currentAgent?.name?.[0]?.toUpperCase() ?? "A"}</AvatarFallback>
                      </Avatar>
                      <StreamBubble steps={streamSteps} content={streamContent} thinking={sending && !streamContent && !streamError && streamSteps.length === 0} />
                      {streamError && <div className="text-destructive">{streamError}</div>}
                    </div>
                  )}

                  <div ref={messagesEndRef} />
                </div>
              )}
            </div>

            <div className="border-t p-4">
              <div className="mx-auto flex max-w-3xl gap-2">
                <Input
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
                  placeholder="输入消息..."
                  disabled={sending}
                  className="flex-1"
                />
                <Button onClick={handleSend} disabled={sending || !input.trim()}>
                  <Send className="size-4" />
                </Button>
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
