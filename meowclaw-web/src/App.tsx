import { BrowserRouter, Routes, Route, Navigate, useNavigate } from "react-router-dom";
import { ThemeProvider } from "next-themes";
import { TooltipProvider } from "@/components/ui/tooltip";
import { Toaster } from "@/components/ui/sonner";
import { useAuthStore } from "@/stores/auth";
import { checkInit } from "@/services/auth";
import { MainLayout } from "@/components/layout/MainLayout";
import { InitPage } from "@/pages/InitPage";
import { LoginPage } from "@/pages/LoginPage";
import { UserSettingsPage } from "@/pages/UserSettingsPage";
import { LlmConfigPage } from "@/pages/LlmConfigPage";
import { EmbeddingModelConfigPage } from "@/pages/EmbeddingModelConfigPage";
import { AgentConfigPage } from "@/pages/AgentConfigPage";
import { ChatPage } from "@/pages/ChatPage";
import { ScheduledTaskPage } from "@/pages/ScheduledTaskPage";
import { McpServicePage } from "@/pages/McpServicePage";
import { SkillPage } from "@/pages/SkillPage";
import { TokensStatisticsPage } from "@/pages/TokensStatisticsPage";
import { WorkspacePage } from "@/pages/WorkspacePage";
import { useEffect, useState } from "react";

function AuthGuard({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuthStore();
  if (loading) return null;
  if (!user) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function GuestRoute({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuthStore();
  if (loading) return null;
  if (user) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function InitGuard({ children }: { children: React.ReactNode }) {
  const { user, loading } = useAuthStore();
  const navigate = useNavigate();
  const [needsInit, setNeedsInit] = useState<boolean | null>(null);

  useEffect(() => {
    if (loading) return;
    if (user) {
      setNeedsInit(false);
      return;
    }
    checkInit().then((needInit) => {
      if (!needInit) {
        setNeedsInit(false);
      } else {
        setNeedsInit(true);
        navigate("/init", { replace: true });
      }
    });
  }, [user, loading, navigate]);

  if (loading || needsInit === null) return null;
  if (needsInit) return null;
  return <>{children}</>;
}

function InitRoute() {
  const { user, loading } = useAuthStore();
  const [checked, setChecked] = useState(false);
  const navigate = useNavigate();

  useEffect(() => {
    if (loading) return;
    if (user) {
      navigate("/", { replace: true });
      return;
    }
    checkInit().then((needInit) => {
      if (!needInit) {
        navigate("/login", { replace: true });
      } else {
        setChecked(true);
      }
    });
  }, [user, loading, navigate]);

  if (!checked) return null;
  return <InitPage />;
}

function AppRoutes() {
  const { fetchUser } = useAuthStore();

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  return (
    <Routes>
      <Route
        path="/init"
        element={<InitRoute />}
      />
      <Route
        path="/login"
        element={
          <InitGuard>
            <GuestRoute>
              <LoginPage />
            </GuestRoute>
          </InitGuard>
        }
      />
      <Route
        path="/"
        element={
          <AuthGuard>
            <MainLayout />
          </AuthGuard>
        }
      >
        <Route index element={<ChatPage />} />
        <Route path="settings" element={<UserSettingsPage />} />
        <Route path="llm" element={<LlmConfigPage />} />
        <Route path="embedding-model" element={<EmbeddingModelConfigPage />} />
        <Route path="agent" element={<AgentConfigPage />} />
        <Route path="scheduled-task" element={<ScheduledTaskPage />} />
        <Route path="mcp-service" element={<McpServicePage />} />
        <Route path="skill" element={<SkillPage />} />
        <Route path="tokens" element={<TokensStatisticsPage />} />
        <Route path="workspace" element={<WorkspacePage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
      <TooltipProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
        <Toaster richColors position="top-center" />
      </TooltipProvider>
    </ThemeProvider>
  );
}

export default App;
