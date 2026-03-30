import React, { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import {
  Trash2,
  MessageSquare,
  Bot,
  ChevronLeft,
  ChevronRight,
  MessageCircle,
  Clock,
  Eye,
  Search,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
} from "@/components/ui/pagination";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { ScrollArea } from "@/components/ui/scroll-area";
import {
  conversationService,
  type ConversationDto,
  TYPE_CHAT,
  TYPE_SCHEDULED,
} from "@/services/conversation";
import {
  agentConfigService,
  type AgentConfigDto,
} from "@/services/agent-config";

interface MessageDto {
  id: number;
  role: string;
  content: string;
  timestamp: number;
  inputTokens?: number;
  outputTokens?: number;
}

export const ConversationManager: React.FC = () => {
  const navigate = useNavigate();
  const [conversations, setConversations] = useState<ConversationDto[]>([]);
  const [agents, setAgents] = useState<AgentConfigDto[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<string>("all");
  const [selectedType, setSelectedType] = useState<string>("all");
  const [searchInput, setSearchInput] = useState("");
  const [searchKeyword, setSearchKeyword] = useState("");
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingConversationId, setDeletingConversationId] = useState<
    number | null
  >(null);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

  const [messageDialogOpen, setMessageDialogOpen] = useState(false);
  const [selectedConversation, setSelectedConversation] =
    useState<ConversationDto | null>(null);
  const [messages, setMessages] = useState<MessageDto[]>([]);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);

  const loadAgents = useCallback(async () => {
    try {
      const response = await agentConfigService.list();
      if (response.code === 200 && response.data) {
        setAgents(response.data);
      }
    } catch (error) {
      console.error("加载智能体失败", error);
    }
  }, []);

  const loadConversations = useCallback(async () => {
    setIsInitialLoading(true);
    try {
      const agentConfigId =
        selectedAgent === "all" ? undefined : parseInt(selectedAgent);
      const response = await conversationService.list({
        page,
        pageSize,
        agentConfigId,
        keyword: searchKeyword || undefined,
      });
      if (response.code === 200 && response.data) {
        let items = response.data.items;
        if (selectedType !== "all") {
          items = items.filter((c) => c.type === selectedType);
        }
        setConversations(items);
        setTotal(response.data.total);
        setTotalPages(response.data.totalPages);
      }
    } catch (error) {
      console.error("加载会话失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  }, [page, selectedAgent, selectedType, searchKeyword]);

  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    setPage(1);
  }, [selectedAgent, selectedType, searchKeyword]);

  const handleSearch = () => {
    setSearchKeyword(searchInput);
    setPage(1);
  };

  const handleDelete = (id: number) => {
    setDeletingConversationId(id);
    setDeleteDialogOpen(true);
  };

  const confirmDelete = async () => {
    if (!deletingConversationId) return;

    try {
      const response = await conversationService.delete(deletingConversationId);
      if (response.code === 200) {
        loadConversations();
      }
    } catch (error) {
      console.error("删除会话失败", error);
    } finally {
      setDeleteDialogOpen(false);
      setDeletingConversationId(null);
    }
  };

  const handleViewMessages = async (conv: ConversationDto) => {
    setSelectedConversation(conv);
    setMessageDialogOpen(true);
    setIsLoadingMessages(true);
    setMessages([]);

    try {
      const response = await conversationService.listMessages(conv.id!);
      if (response.code === 200 && response.data) {
        setMessages(response.data);
      }
    } catch (error) {
      console.error("加载消息失败", error);
    } finally {
      setIsLoadingMessages(false);
    }
  };

  const getAgentName = (agentConfigId: number) => {
    const agent = agents.find((a) => a.id === agentConfigId);
    return agent?.name || "未知";
  };

  const getAgent = (agentConfigId: number) => {
    return agents.find((a) => a.id === agentConfigId);
  };

  const formatTimestamp = (timestamp: number) => {
    return new Date(timestamp).toLocaleString("zh-CN");
  };

  const renderMessageContent = (content: string) => {
    if (content.length > 200) {
      return content.substring(0, 200) + "...";
    }
    return content;
  };

  return (
    <div className="p-6 h-full flex flex-col min-h-0">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="text-2xl font-bold">会话</h1>
          <p className="text-sm text-muted-foreground mt-1">
            管理所有对话会话历史
          </p>
        </div>
        <div className="text-sm text-muted-foreground">共 {total} 条记录</div>
      </div>

      <div className="flex gap-4 mb-6">
        <div className="flex-1">
          <Input
            placeholder="搜索会话..."
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter") {
                handleSearch();
              }
            }}
          />
        </div>
        <Select value={selectedType} onValueChange={setSelectedType}>
          <SelectTrigger className="w-32">
            <SelectValue placeholder="会话类型" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部类型</SelectItem>
            <SelectItem value={TYPE_CHAT}>聊天会话</SelectItem>
            <SelectItem value={TYPE_SCHEDULED}>定时任务</SelectItem>
          </SelectContent>
        </Select>
        <Select value={selectedAgent} onValueChange={setSelectedAgent}>
          <SelectTrigger className="w-48">
            <SelectValue placeholder="选择智能体" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">全部智能体</SelectItem>
            {agents.map((agent) => (
              <SelectItem key={agent.id} value={agent.id?.toString() || ""}>
                {agent.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button onClick={handleSearch}>
          <Search className="h-4 w-4" />
          搜索
        </Button>
      </div>

      <div className="border rounded-lg overflow-auto">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead className="text-left">标题</TableHead>
              <TableHead className="text-center">智能体</TableHead>
              <TableHead className="text-center">类型</TableHead>
              <TableHead className="text-center w-36">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isInitialLoading ? (
              <TableRow>
                <TableCell colSpan={4} className="text-center py-12">
                  <Spinner className="size-6 mx-auto" />
                </TableCell>
              </TableRow>
            ) : conversations.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={4}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无会话
                </TableCell>
              </TableRow>
            ) : (
              conversations.map((conv) => {
                const isScheduled = conv.type === TYPE_SCHEDULED;
                return (
                  <TableRow key={conv.id}>
                    <TableCell className="text-center">
                      <div className="flex items-center justify-start gap-2">
                        <MessageSquare className="h-4 w-4 text-muted-foreground" />
                        <span className="font-medium">{conv.title}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <div className="flex items-center justify-center gap-2">
                        <Avatar className="h-6 w-6">
                          <AvatarImage
                            src={getAgent(conv.agentConfigId)?.avatar}
                          />
                          <AvatarFallback className="bg-secondary">
                            <Bot className="h-3.5 w-3.5" />
                          </AvatarFallback>
                        </Avatar>
                        {conv.agentName || getAgentName(conv.agentConfigId)}
                      </div>
                    </TableCell>
                    <TableCell className="text-center">
                      <Badge variant={isScheduled ? "secondary" : "default"}>
                        {isScheduled ? "定时任务" : "聊天"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-center">
                      <div className="flex items-center justify-center gap-1">
                        {isScheduled ? (
                          <Button
                            variant="ghost"
                            size="icon"
                            title="查看消息"
                            onClick={() => handleViewMessages(conv)}
                          >
                            <Eye className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            variant="ghost"
                            size="icon"
                            title="进入聊天"
                            onClick={() =>
                              conv.id &&
                              conv.agentConfigId &&
                              navigate(`/chat/${conv.agentConfigId}/${conv.id}`)
                            }
                          >
                            <MessageCircle className="h-4 w-4" />
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="icon"
                          title="删除"
                          onClick={() => conv.id && handleDelete(conv.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </div>

      {totalPages > 1 && (
        <div className="mt-4">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <Button
                  variant="ghost"
                  size="icon"
                  disabled={page === 1}
                  onClick={() => setPage(page - 1)}
                >
                  <ChevronLeft className="h-4 w-4" />
                </Button>
              </PaginationItem>
              {Array.from({ length: totalPages }, (_, i) => i + 1).map((p) => (
                <PaginationItem key={p}>
                  <Button
                    variant={page === p ? "outline" : "ghost"}
                    size="icon"
                    onClick={() => setPage(p)}
                  >
                    {p}
                  </Button>
                </PaginationItem>
              ))}
              <PaginationItem>
                <Button
                  variant="ghost"
                  size="icon"
                  disabled={page === totalPages}
                  onClick={() => setPage(page + 1)}
                >
                  <ChevronRight className="h-4 w-4" />
                </Button>
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
      )}

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
            <AlertDialogAction onClick={confirmDelete}>删除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={messageDialogOpen} onOpenChange={setMessageDialogOpen}>
        <DialogContent className="max-w-2xl max-h-[80vh]">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Clock className="h-5 w-5" />
              定时任务会话消息
              {selectedConversation && (
                <span className="text-muted-foreground font-normal">
                  - {selectedConversation.title}
                </span>
              )}
            </DialogTitle>
          </DialogHeader>
          <ScrollArea className="h-[60vh]">
            {isLoadingMessages ? (
              <div className="flex items-center justify-center py-8">
                <Spinner className="size-6" />
              </div>
            ) : messages.length === 0 ? (
              <div className="text-center text-muted-foreground py-8">
                暂无消息
              </div>
            ) : (
              <div className="space-y-4 p-2">
                {messages.map((msg) => (
                  <div
                    key={msg.id}
                    className={`p-3 rounded-lg ${
                      msg.role === "user"
                        ? "bg-primary/10 ml-0 mr-8"
                        : "bg-muted ml-8 mr-0"
                    }`}
                  >
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-xs font-medium text-muted-foreground">
                        {msg.role === "user" ? "用户" : "助手"}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {formatTimestamp(msg.timestamp)}
                      </span>
                      {(msg.inputTokens || msg.outputTokens) && (
                        <span className="text-xs text-muted-foreground">
                          (入: {msg.inputTokens || 0} / 出:{" "}
                          {msg.outputTokens || 0})
                        </span>
                      )}
                    </div>
                    <div className="text-sm whitespace-pre-wrap">
                      {renderMessageContent(msg.content)}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </ScrollArea>
        </DialogContent>
      </Dialog>
    </div>
  );
};
