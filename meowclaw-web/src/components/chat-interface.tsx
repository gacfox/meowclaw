import React, { useState, useRef, useEffect } from "react";
import {
  Send,
  Loader2,
  Bot,
  Plus,
  Terminal,
  Trash2,
  MoreHorizontal,
  Pencil,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Input } from "@/components/ui/input";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
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
import { MarkdownRenderer } from "@/components/markdown-renderer";
import { chatService } from "@/services/chat";
import type { ChatStreamEvent } from "@/services/chat";
import {
  conversationService,
  type ConversationDto,
} from "@/services/conversation";
import {
  agentConfigService,
  type AgentConfigDto,
} from "@/services/agent-config";
import { useUserStore } from "@/stores/userStore";
import { useAppStore } from "@/stores/appStore";

interface Message {
  id: string;
  role: "user" | "assistant" | "tool";
  content?: string;
  isStreaming?: boolean;
  toolPayload?: ToolPayload;
}

interface ToolPayload {
  toolName: string;
  args?: string;
  result?: string;
}

interface MessageGroup {
  message?: Message;
  tools: Message[];
}

type AgentItem = AgentConfigDto & { id: number };
type ConversationItem = ConversationDto & { id: number };

const isAgentItem = (agent: AgentConfigDto): agent is AgentItem =>
  typeof agent.id === "number";

const isConversationItem = (conv: ConversationDto): conv is ConversationItem =>
  typeof conv.id === "number";

const parseToolPayload = (raw?: string): ToolPayload => {
  if (!raw) {
    return { toolName: "unknown" };
  }
  try {
    const parsed = JSON.parse(raw);
    if (parsed && typeof parsed === "object") {
      return {
        toolName: parsed.toolName || "unknown",
        args: parsed.args,
        result: parsed.result,
      };
    }
  } catch {
    // ignore
  }
  return { toolName: "unknown", args: raw };
};

const formatToolArgs = (args?: string) => {
  if (!args) return "无";
  try {
    const parsed = JSON.parse(args);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return args;
  }
};

const formatToolResult = (result?: string) => {
  if (!result) return "无";
  return result;
};

const buildMessageGroups = (messages: Message[]): MessageGroup[] => {
  const groups: MessageGroup[] = [];
  let pendingTools: Message[] = [];
  messages.forEach((message) => {
    if (message.role === "tool") {
      pendingTools.push(message);
      return;
    }
    groups.push({ message, tools: pendingTools });
    pendingTools = [];
  });
  if (pendingTools.length > 0) {
    groups.push({ tools: pendingTools });
  }
  return groups;
};

export const ChatInterface: React.FC = () => {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isCreatingConversation, setIsCreatingConversation] = useState(false);
  const [agents, setAgents] = useState<AgentItem[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentItem | null>(null);
  const [conversations, setConversations] = useState<ConversationItem[]>([]);
  const [isConversationsLoading, setIsConversationsLoading] = useState(false);
  const [currentConversation, setCurrentConversation] =
    useState<ConversationItem | null>(null);
  const currentConversationIdRef = useRef<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const user = useUserStore((state) => state.user);
  const { lastSelectedAgentId, setLastSelectedAgentId } = useAppStore();
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingConversationId, setDeletingConversationId] = useState<
    number | null
  >(null);
  const [generatingTitleId, setGeneratingTitleId] = useState<number | null>(
    null,
  );
  const [renamingConversationId, setRenamingConversationId] = useState<
    number | null
  >(null);
  const [renamingTitle, setRenamingTitle] = useState("");
  const [conversationPage, setConversationPage] = useState(1);
  const [hasMoreConversations, setHasMoreConversations] = useState(true);
  const [isLoadingMoreConversations, setIsLoadingMoreConversations] =
    useState(false);
  const conversationListRef = useRef<HTMLDivElement>(null);
  const conversationPageSize = 20;

  useEffect(() => {
    loadAgents();
  }, []);

  useEffect(() => {
    if (selectedAgent) {
      setConversations([]);
      setConversationPage(1);
      setHasMoreConversations(true);
      loadConversations(selectedAgent.id, 1, true);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedAgent]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const loadAgents = async () => {
    try {
      const response = await agentConfigService.list();
      if (response.code === 200 && response.data) {
        const availableAgents = response.data.filter(isAgentItem);
        setAgents(availableAgents);
        if (availableAgents.length > 0) {
          const lastAgent = lastSelectedAgentId
            ? availableAgents.find((a) => a.id === lastSelectedAgentId)
            : null;
          setSelectedAgent(lastAgent || availableAgents[0]);
        }
      }
    } catch (error) {
      console.error("加载智能体失败", error);
    }
  };

  const loadConversations = async (
    agentId: number,
    page: number = 1,
    reset: boolean = false,
  ) => {
    if (reset) {
      setIsConversationsLoading(true);
    } else {
      setIsLoadingMoreConversations(true);
    }
    try {
      const response = await conversationService.list({
        agentConfigId: agentId,
        page,
        pageSize: conversationPageSize,
      });
      if (response.code === 200 && response.data) {
        const availableConversations =
          response.data.items.filter(isConversationItem);
        if (reset) {
          setConversations(availableConversations);
          if (
            !currentConversationIdRef.current &&
            availableConversations.length > 0
          ) {
            setCurrentConversation(availableConversations[0]);
            currentConversationIdRef.current = availableConversations[0].id;
            loadConversationMessages(availableConversations[0].id);
          }
        } else {
          setConversations((prev) => [...prev, ...availableConversations]);
        }
        setHasMoreConversations(
          page < response.data.totalPages && availableConversations.length > 0,
        );
      }
    } catch (error) {
      console.error("加载会话失败", error);
    } finally {
      setIsConversationsLoading(false);
      setIsLoadingMoreConversations(false);
    }
  };

  const handleConversationScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const target = e.target as HTMLDivElement;
    const { scrollTop, scrollHeight, clientHeight } = target;
    if (
      scrollHeight - scrollTop - clientHeight < 100 &&
      hasMoreConversations &&
      !isLoadingMoreConversations
    ) {
      const nextPage = conversationPage + 1;
      setConversationPage(nextPage);
      if (selectedAgent) {
        loadConversations(selectedAgent.id, nextPage, false);
      }
    }
  };

  const loadConversationMessages = async (conversationId: number) => {
    try {
      const response = await conversationService.listMessages(conversationId);
      if (response.code === 200 && response.data) {
        const mapped = response.data
          .filter(
            (msg) =>
              msg.role === "user" ||
              msg.role === "assistant" ||
              msg.role === "tool",
          )
          .map((msg, idx) => ({
            id: `${conversationId}-${msg.timestamp}-${idx}`,
            role: msg.role as "user" | "assistant" | "tool",
            content: msg.role === "tool" ? undefined : msg.content,
            isStreaming: false,
            toolPayload:
              msg.role === "tool" ? parseToolPayload(msg.content) : undefined,
          }));
        setMessages(mapped);
      }
    } catch (error) {
      console.error("加载对话消息失败", error);
    }
  };

  const createNewConversation = async () => {
    if (!selectedAgent) return;
    if (isCreatingConversation) return;
    if (
      currentConversation &&
      currentConversation.title === "新对话" &&
      messages.length === 0
    ) {
      return;
    }

    try {
      setIsCreatingConversation(true);
      const response = await conversationService.create({
        agentConfigId: selectedAgent.id,
        title: "新对话",
      });
      if (
        response.code === 200 &&
        response.data &&
        isConversationItem(response.data)
      ) {
        setCurrentConversation(response.data);
        currentConversationIdRef.current = response.data.id;
        setMessages([]);
        setConversationPage(1);
        loadConversations(selectedAgent.id, 1, true);
      }
    } catch (error) {
      console.error("创建会话失败", error);
    } finally {
      setIsCreatingConversation(false);
    }
  };

  const handleDeleteConversation = (conversationId: number) => {
    setDeletingConversationId(conversationId);
    setDeleteDialogOpen(true);
  };

  const confirmDeleteConversation = async () => {
    if (!selectedAgent || !deletingConversationId) return;

    try {
      const response = await conversationService.delete(deletingConversationId);
      if (response.code === 200) {
        if (currentConversation?.id === deletingConversationId) {
          setCurrentConversation(null);
          currentConversationIdRef.current = null;
          setMessages([]);
        }
        if (selectedAgent) {
          loadConversations(selectedAgent.id, 1, true);
          setConversationPage(1);
        }
      }
    } catch (error) {
      console.error("删除会话失败", error);
    } finally {
      setDeleteDialogOpen(false);
      setDeletingConversationId(null);
    }
  };

  const handleRenameConversation = (
    conversationId: number,
    currentTitle: string,
  ) => {
    setRenamingConversationId(conversationId);
    setRenamingTitle(currentTitle);
  };

  const confirmRenameConversation = async () => {
    if (!renamingConversationId || !renamingTitle.trim()) return;

    try {
      const conv = conversations.find((c) => c.id === renamingConversationId);
      if (!conv) return;

      const response = await conversationService.update(
        renamingConversationId,
        {
          agentConfigId: conv.agentConfigId,
          title: renamingTitle.trim(),
        },
      );

      if (response.code === 200 && response.data) {
        setConversations((prev) =>
          prev.map((c) =>
            c.id === renamingConversationId
              ? { ...c, title: response.data!.title }
              : c,
          ),
        );
        if (currentConversation?.id === renamingConversationId) {
          setCurrentConversation((prev) =>
            prev ? { ...prev, title: response.data!.title } : prev,
          );
        }
      }
    } catch (error) {
      console.error("重命名会话失败", error);
    } finally {
      setRenamingConversationId(null);
      setRenamingTitle("");
    }
  };

  const handleSubmit = async () => {
    if (!input.trim() || isLoading || !selectedAgent) return;

    const userMessage: Message = {
      id: Date.now().toString(),
      role: "user",
      content: input.trim(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput("");
    setIsLoading(true);

    let conversationId =
      currentConversationIdRef.current ?? currentConversation?.id;

    if (!conversationId) {
      try {
        const response = await conversationService.create({
          agentConfigId: selectedAgent.id,
          title: "新对话",
        });
        if (
          response.code === 200 &&
          response.data &&
          isConversationItem(response.data)
        ) {
          conversationId = response.data.id;
          setCurrentConversation(response.data);
          currentConversationIdRef.current = response.data.id;
          loadConversations(selectedAgent.id);
        }
      } catch (error) {
        console.error("创建会话失败", error);
      }
    }

    if (!conversationId) {
      setIsLoading(false);
      return;
    }

    const assistantMessage: Message = {
      id: (Date.now() + 1).toString(),
      role: "assistant",
      content: "",
      isStreaming: true,
    };

    setMessages((prev) => [...prev, assistantMessage]);

    try {
      const response = await chatService.chatStream({
        conversationId,
        content: userMessage.content || "",
      });

      if (!response.ok || !response.body) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (reader) {
        let buffer = "";

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split("\n");
          buffer = lines.pop() || "";

          for (const line of lines) {
            if (line.startsWith("data:")) {
              const data = line.slice(5).trimStart();

              try {
                const event: ChatStreamEvent = JSON.parse(data);

                if (event.type === "tool_call") {
                  const payload = parseToolPayload(event.content);
                  setMessages((prev) =>
                    insertToolMessage(prev, buildToolMessage(payload)),
                  );
                  continue;
                }

                if (event.type === "tool_result") {
                  const payload = parseToolPayload(event.content);
                  setMessages((prev) => updateToolResult(prev, payload));
                  continue;
                }

                setMessages((prev) => {
                  const lastMsg = prev[prev.length - 1];
                  if (lastMsg.role !== "assistant") return prev;

                  const updatedMsg = { ...lastMsg };

                  switch (event.type) {
                    case "content":
                      updatedMsg.content =
                        (updatedMsg.content || "") + event.content;
                      break;
                    case "finish":
                      updatedMsg.isStreaming = false;
                      break;
                    case "error":
                      updatedMsg.content =
                        (updatedMsg.content || "") + "\n错误: " + event.content;
                      updatedMsg.isStreaming = false;
                      break;
                  }

                  return [...prev.slice(0, -1), updatedMsg];
                });
              } catch (e) {
                console.error("解析流数据失败", e, data);
              }
            }
          }
        }

        if (buffer.startsWith("data:")) {
          try {
            const event: ChatStreamEvent = JSON.parse(
              buffer.slice(5).trimStart(),
            );
            setMessages((prev) =>
              prev.map((msg, idx) =>
                idx === prev.length - 1 && msg.role === "assistant"
                  ? {
                      ...msg,
                      isStreaming:
                        event.type === "finish" ? false : msg.isStreaming,
                    }
                  : msg,
              ),
            );
          } catch (e) {
            // ignore
          }
        }

        setMessages((prev) =>
          prev.map((msg, idx) =>
            idx === prev.length - 1 && msg.role === "assistant"
              ? { ...msg, isStreaming: false }
              : msg,
          ),
        );
      }
    } catch (error) {
      console.error("发送消息失败", error);
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantMessage.id
            ? { ...msg, content: "发送失败，请重试", isStreaming: false }
            : msg,
        ),
      );
    } finally {
      setIsLoading(false);
      if (selectedAgent) {
        loadConversations(selectedAgent.id, 1, true);
        setConversationPage(1);
      }

      if (conversationId && currentConversation?.title === "新对话") {
        setGeneratingTitleId(conversationId);
        try {
          const titleResponse =
            await conversationService.generateTitle(conversationId);
          if (titleResponse.code === 200 && titleResponse.data) {
            setConversations((prev) =>
              prev.map((conv) =>
                conv.id === conversationId
                  ? { ...conv, title: titleResponse.data!.title }
                  : conv,
              ),
            );
            if (currentConversation?.id === conversationId) {
              setCurrentConversation((prev) =>
                prev ? { ...prev, title: titleResponse.data!.title } : prev,
              );
            }
          }
        } catch (error) {
          console.error("生成标题失败", error);
        } finally {
          setGeneratingTitleId(null);
        }
      }
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  const buildToolMessage = (payload: ToolPayload): Message => ({
    id: `${Date.now()}-${Math.random()}`,
    role: "tool",
    toolPayload: payload,
  });

  const insertToolMessage = (prev: Message[], toolMessage: Message) => {
    let assistantIndex = -1;
    for (let i = prev.length - 1; i >= 0; i -= 1) {
      if (prev[i].role === "assistant" && prev[i].isStreaming) {
        assistantIndex = i;
        break;
      }
    }
    if (assistantIndex === -1) {
      return [...prev, toolMessage];
    }
    const next = [...prev];
    next.splice(assistantIndex, 0, toolMessage);
    return next;
  };

  const updateToolResult = (prev: Message[], payload: ToolPayload) => {
    const next = [...prev];
    for (let i = next.length - 1; i >= 0; i -= 1) {
      const message = next[i];
      if (message.role === "tool" && message.toolPayload) {
        if (
          message.toolPayload.toolName === payload.toolName &&
          !message.toolPayload.result
        ) {
          next[i] = {
            ...message,
            toolPayload: {
              ...message.toolPayload,
              result: payload.result,
            },
          };
          return next;
        }
      }
    }
    return insertToolMessage(prev, buildToolMessage(payload));
  };

  return (
    <div className="flex h-full min-h-0">
      {/* 会话列表侧边栏 */}
      <div className="w-64 border-r bg-muted/30 flex flex-col min-h-0">
        <div className="p-4 border-b">
          <Button
            variant="outline"
            className="w-full justify-start gap-2"
            onClick={createNewConversation}
            disabled={
              isCreatingConversation ||
              (currentConversation?.title === "新对话" && messages.length === 0)
            }
          >
            <Plus className="h-4 w-4" />
            新建对话
          </Button>
        </div>
        <ScrollArea
          className="w-64 flex-1 min-h-0"
          ref={conversationListRef}
          onScroll={handleConversationScroll}
        >
          <div className="w-64 p-2 space-y-1">
            {isConversationsLoading && conversationPage === 1 ? (
              <>
                {[1, 2, 3, 4, 5].map((i) => (
                  <div key={i} className="flex items-center gap-1">
                    <Skeleton className="h-9 flex-1 rounded-md" />
                  </div>
                ))}
              </>
            ) : (
              <>
                {conversations.map((conv) => (
                  <div
                    key={conv.id}
                    className="group flex items-center gap-1 w-full min-w-0"
                  >
                    {renamingConversationId === conv.id ? (
                      <form
                        className="flex-1 flex items-center gap-1"
                        onSubmit={(e) => {
                          e.preventDefault();
                          confirmRenameConversation();
                        }}
                      >
                        <Input
                          value={renamingTitle}
                          onChange={(e) => setRenamingTitle(e.target.value)}
                          className="h-8 text-sm"
                          autoFocus
                          onBlur={() => {
                            if (renamingTitle.trim()) {
                              confirmRenameConversation();
                            } else {
                              setRenamingConversationId(null);
                              setRenamingTitle("");
                            }
                          }}
                          onKeyDown={(e) => {
                            if (e.key === "Escape") {
                              setRenamingConversationId(null);
                              setRenamingTitle("");
                            }
                          }}
                        />
                      </form>
                    ) : (
                      <>
                        <Button
                          variant={
                            currentConversation?.id === conv.id
                              ? "secondary"
                              : "ghost"
                          }
                          className="flex-1 min-w-0 justify-start text-left overflow-hidden"
                          onClick={() => {
                            setCurrentConversation(conv);
                            currentConversationIdRef.current = conv.id;
                            loadConversationMessages(conv.id);
                          }}
                        >
                          {generatingTitleId === conv.id ? (
                            <Spinner className="h-4 w-4" />
                          ) : (
                            <span className="truncate">{conv.title}</span>
                          )}
                        </Button>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <div
                              className="shrink-0 h-8 w-8 flex items-center justify-center rounded-md cursor-pointer opacity-0 group-hover:opacity-100 data-[state=open]:opacity-100 transition-opacity hover:bg-accent"
                              onClick={(e) => e.stopPropagation()}
                            >
                              <MoreHorizontal className="h-4 w-4" />
                            </div>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem
                              onClick={(e) => {
                                e.stopPropagation();
                                handleRenameConversation(conv.id, conv.title);
                              }}
                            >
                              <Pencil className="h-4 w-4 mr-2" />
                              重命名
                            </DropdownMenuItem>
                            <DropdownMenuItem
                              variant="destructive"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleDeleteConversation(conv.id);
                              }}
                            >
                              <Trash2 className="h-4 w-4 mr-2" />
                              删除
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </>
                    )}
                  </div>
                ))}
                {isLoadingMoreConversations && (
                  <>
                    {[1, 2, 3].map((i) => (
                      <div key={`loading-${i}`} className="flex items-center">
                        <Skeleton className="h-9 flex-1 rounded-md" />
                      </div>
                    ))}
                  </>
                )}
              </>
            )}
          </div>
        </ScrollArea>

        <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>确认删除</AlertDialogTitle>
              <AlertDialogDescription>
                确定要删除这个会话吗？此操作无法撤销。
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>取消</AlertDialogCancel>
              <AlertDialogAction onClick={confirmDeleteConversation}>
                删除
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <div className="p-4 border-t">
          <Select
            value={selectedAgent?.id?.toString() || ""}
            onValueChange={(value) => {
              const agent = agents.find((a) => a.id === parseInt(value));
              if (agent) {
                setSelectedAgent(agent);
                setLastSelectedAgentId(agent.id);
                setCurrentConversation(null);
                currentConversationIdRef.current = null;
                setMessages([]);
              }
            }}
          >
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
      </div>

      {/* 对话区域 */}
      <div className="flex-1 flex flex-col min-h-0">
        <ScrollArea className="flex-1 min-h-0 p-4" ref={scrollRef}>
          <div className="space-y-6 max-w-3xl mx-auto">
            {messages.length === 0 && (
              <div className="text-center text-muted-foreground py-20">
                <Bot className="h-12 w-12 mx-auto mb-4 opacity-50" />
                <p className="text-lg font-medium">开始一个新的对话</p>
                <p className="text-sm">选择智能体并发送消息</p>
              </div>
            )}
            {buildMessageGroups(messages).map((group, groupIndex) => {
              const message = group.message;
              const tools = group.tools;
              const isUser = message?.role === "user";
              const isAssistant = message?.role === "assistant";

              return (
                <div
                  key={message?.id || `tool-group-${groupIndex}`}
                  className={`flex gap-4 ${isUser ? "flex-row-reverse" : ""}`}
                >
                  <Avatar className="h-8 w-8 shrink-0">
                    {isUser ? (
                      <>
                        <AvatarImage src={user?.avatarUrl} />
                        <AvatarFallback className="bg-primary text-primary-foreground">
                          {user?.displayUsername?.slice(0, 2).toUpperCase() ||
                            "U"}
                        </AvatarFallback>
                      </>
                    ) : (
                      <>
                        <AvatarImage src={selectedAgent?.avatar} />
                        <AvatarFallback className="bg-secondary">
                          <Bot className="h-4 w-4" />
                        </AvatarFallback>
                      </>
                    )}
                  </Avatar>
                  <div
                    className={`max-w-[80%] rounded-2xl px-4 py-3 ${
                      isUser
                        ? "bg-primary text-primary-foreground rounded-br-sm"
                        : "bg-muted rounded-bl-sm"
                    }`}
                  >
                    {isUser ? (
                      <p className="text-sm whitespace-pre-wrap">
                        {message?.content}
                      </p>
                    ) : (
                      <div className="text-sm">
                        {tools.length > 0 && (
                          <div className="space-y-2 mb-3">
                            {tools.map((toolMessage) => (
                              <Collapsible key={toolMessage.id}>
                                <div className="rounded-lg border border-amber-200 bg-amber-50">
                                  <CollapsibleTrigger className="w-full text-left px-3 py-2 flex items-center justify-between text-xs font-medium text-amber-700">
                                    <span className="flex items-center gap-2">
                                      <Terminal className="h-3 w-3" />
                                      工具:{" "}
                                      <span className="font-mono">
                                        {toolMessage.toolPayload?.toolName ||
                                          "unknown"}
                                      </span>
                                    </span>
                                    <span className="text-amber-600/70">
                                      详情
                                    </span>
                                  </CollapsibleTrigger>
                                  <CollapsibleContent className="px-3 pb-3 text-xs space-y-2">
                                    <div>
                                      <div className="text-amber-700/80">
                                        参数
                                      </div>
                                      <pre className="mt-1 rounded-md bg-background/70 p-2 text-xs whitespace-pre-wrap overflow-x-auto">
                                        {formatToolArgs(
                                          toolMessage.toolPayload?.args,
                                        )}
                                      </pre>
                                    </div>
                                    <div>
                                      <div className="text-amber-700/80">
                                        结果
                                      </div>
                                      <pre className="mt-1 rounded-md bg-background/70 p-2 text-xs whitespace-pre-wrap overflow-x-auto">
                                        {toolMessage.toolPayload?.result
                                          ? formatToolResult(
                                              toolMessage.toolPayload?.result,
                                            )
                                          : "等待结果..."}
                                      </pre>
                                    </div>
                                  </CollapsibleContent>
                                </div>
                              </Collapsible>
                            ))}
                          </div>
                        )}
                        {isAssistant && (
                          <MarkdownRenderer content={message?.content || ""} />
                        )}
                        {isAssistant && message?.isStreaming && (
                          <span className="inline-block w-2 h-4 ml-1 bg-current animate-pulse" />
                        )}
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </ScrollArea>

        {/* 输入区域 */}
        <div className="border-t p-4">
          <div className="max-w-3xl mx-auto relative">
            <Textarea
              ref={textareaRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入消息... (Shift+Enter换行)"
              className="min-h-15 pr-14 resize-none"
              disabled={isLoading}
            />
            <Button
              size="icon"
              className="absolute right-2 bottom-2 h-8 w-8"
              onClick={handleSubmit}
              disabled={isLoading || !input.trim() || !selectedAgent}
            >
              {isLoading ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Send className="h-4 w-4" />
              )}
            </Button>
          </div>
          <p className="text-xs text-center text-muted-foreground mt-2">
            AI生成的内容可能存在错误，请仔细核实
          </p>
        </div>
      </div>
    </div>
  );
};
