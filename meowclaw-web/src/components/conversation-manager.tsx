import React, { useState, useEffect, useCallback } from "react";
import {
  Trash2,
  MessageSquare,
  Bot,
  ChevronLeft,
  ChevronRight,
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
  Pagination,
  PaginationContent,
  PaginationItem,
} from "@/components/ui/pagination";
import {
  conversationService,
  type ConversationDto,
} from "@/services/conversation";
import {
  agentConfigService,
  type AgentConfigDto,
} from "@/services/agent-config";

export const ConversationManager: React.FC = () => {
  const [conversations, setConversations] = useState<ConversationDto[]>([]);
  const [agents, setAgents] = useState<AgentConfigDto[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<string>("all");
  const [searchQuery, setSearchQuery] = useState("");
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deletingConversationId, setDeletingConversationId] = useState<
    number | null
  >(null);
  const [isInitialLoading, setIsInitialLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [total, setTotal] = useState(0);
  const pageSize = 10;

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
      });
      if (response.code === 200 && response.data) {
        setConversations(response.data.items);
        setTotal(response.data.total);
        setTotalPages(response.data.totalPages);
      }
    } catch (error) {
      console.error("加载会话失败", error);
    } finally {
      setIsInitialLoading(false);
    }
  }, [page, selectedAgent]);

  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    setPage(1);
  }, [selectedAgent]);

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

  const getAgentName = (agentConfigId: number) => {
    const agent = agents.find((a) => a.id === agentConfigId);
    return agent?.name || "未知";
  };

  const filteredConversations = conversations.filter((conv) =>
    conv.title.toLowerCase().includes(searchQuery.toLowerCase()),
  );

  return (
    <div className="p-6 max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">会话管理</h1>
        <div className="text-sm text-muted-foreground">共 {total} 条记录</div>
      </div>

      <div className="flex gap-4 mb-6">
        <div className="flex-1">
          <Input
            placeholder="搜索会话..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
          />
        </div>
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
      </div>

      <div className="border rounded-lg">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>标题</TableHead>
              <TableHead>智能体</TableHead>
              <TableHead className="text-center">操作</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isInitialLoading ? (
              <TableRow>
                <TableCell colSpan={3} className="text-center py-12">
                  <Spinner className="size-6 mx-auto" />
                </TableCell>
              </TableRow>
            ) : filteredConversations.length === 0 ? (
              <TableRow>
                <TableCell
                  colSpan={3}
                  className="text-center text-muted-foreground py-8"
                >
                  暂无会话
                </TableCell>
              </TableRow>
            ) : (
              filteredConversations.map((conv) => (
                <TableRow key={conv.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <MessageSquare className="h-4 w-4 text-muted-foreground" />
                      <span className="font-medium">{conv.title}</span>
                    </div>
                  </TableCell>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <Bot className="h-4 w-4 text-muted-foreground" />
                      {getAgentName(conv.agentConfigId)}
                    </div>
                  </TableCell>
                  <TableCell className="text-left">
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => conv.id && handleDelete(conv.id)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
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
    </div>
  );
};
