import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import { InitPage } from "@/pages/init";
import { LoginPage } from "@/pages/login";
import { NotFoundPage } from "@/pages/not-found";
import { StatisticsPage } from "@/pages/statistics";
import { Layout } from "@/components/layout";
import { ChatInterface } from "@/components/chat-interface";
import { LlmManager } from "@/components/llm-manager";
import { AgentManager } from "@/components/agent-manager";
import { ConversationManager } from "@/components/conversation-manager";
import { SystemSettings } from "@/components/system-settings";
import { WorkspaceManager } from "@/components/workspace-manager";
import { McpManager } from "@/components/mcp-manager";
import { useAuthStore } from "@/stores";

function PlaceholderPage({ title }: { title: string }) {
  return (
    <div className="p-6 h-full flex flex-col min-h-0">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">{title}</h1>
        <p className="text-sm text-muted-foreground mt-1">
          {title}管理（功能开发中）
        </p>
      </div>
    </div>
  );
}

export function RoutesView() {
  const { isAuthenticated } = useAuthStore();
  const location = useLocation();

  const requireAuth = (element: React.ReactElement) =>
    isAuthenticated ? (
      element
    ) : (
      <Navigate to="/login" state={{ from: location }} replace />
    );

  return (
    <Routes>
      <Route path="/init" element={<InitPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={requireAuth(<Layout />)}>
        <Route index element={<Navigate to="/chat" replace />} />
        <Route path="chat" element={<ChatInterface />} />
        <Route path="chat/:agentId" element={<ChatInterface />} />
        <Route
          path="chat/:agentId/:conversationId"
          element={<ChatInterface />}
        />
        <Route path="agent" element={<AgentManager />} />
        <Route
          path="agent-team"
          element={<PlaceholderPage title="智能体团队" />}
        />
        <Route path="llm" element={<LlmManager />} />
        <Route path="conversation" element={<ConversationManager />} />
        <Route path="workspace" element={<WorkspaceManager />} />
        <Route path="mcp" element={<McpManager />} />
        <Route path="channel" element={<PlaceholderPage title="频道" />} />
        <Route
          path="scheduled"
          element={<PlaceholderPage title="定时任务" />}
        />
        <Route path="skill" element={<PlaceholderPage title="技能" />} />
        <Route path="statistics" element={<StatisticsPage />} />
        <Route path="settings" element={<SystemSettings />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
