import { Moon, Sun, Monitor, LogOut, Settings } from "lucide-react";
import { siGithub } from "simple-icons";
import { useTheme } from "next-themes";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/stores/auth";
import { SidebarTrigger } from "@/components/ui/sidebar";
import { Button } from "@/components/ui/button";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
} from "@/components/ui/breadcrumb";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

const ROUTE_LABELS: Record<string, string> = {
  "/": "对话",
  "/llm": "LLM 配置",
  "/agent": "智能体",
  "/scheduled-task": "定时任务",
  "/mcp-service": "MCP 服务",
  "/skill": "SKILL",
  "/tokens": "tokens 统计",
  "/settings": "用户设置",
};

export function AppHeader() {
  const { setTheme, theme } = useTheme();
  const { user, logout } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const breadcrumbLabel = ROUTE_LABELS[location.pathname] ?? "MeowClaw";

  const cycleTheme = () => {
    if (theme === "system") setTheme("light");
    else if (theme === "light") setTheme("dark");
    else setTheme("system");
  };

  const themeIcon =
    theme === "dark" ? (
      <Moon className="size-4" />
    ) : theme === "light" ? (
      <Sun className="size-4" />
    ) : (
      <Monitor className="size-4" />
    );

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <header className="flex h-14 shrink-0 items-center gap-2 border-b px-4">
      <SidebarTrigger className="-ml-1" />
      <Breadcrumb className="flex-1">
        <BreadcrumbList>
          <BreadcrumbItem>
            <BreadcrumbPage>{breadcrumbLabel}</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <div className="flex items-center gap-1">
        <Button variant="ghost" size="icon-sm" onClick={cycleTheme}>
          {themeIcon}
        </Button>
        <Button variant="ghost" size="icon-sm" asChild>
          <a
            href="https://github.com/gacfox/meowclaw"
            target="_blank"
            rel="noopener noreferrer"
          >
            <svg role="img" viewBox="0 0 24 24" className="size-4 fill-current"><path d={siGithub.path} /></svg>
          </a>
        </Button>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm">
              <Avatar className="size-6">
                <AvatarImage src={user?.avatarUrl ?? undefined} />
                <AvatarFallback className="text-xs">
                  {user?.displayName?.[0]?.toUpperCase() ?? "U"}
                </AvatarFallback>
              </Avatar>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <div className="px-2 py-1.5 text-sm font-medium">
              {user?.displayName ?? user?.username ?? "User"}
            </div>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={() => navigate("/settings")}>
              <Settings className="mr-2 size-4" />
              用户设置
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem onClick={handleLogout}>
              <LogOut className="mr-2 size-4" />
              退出
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
