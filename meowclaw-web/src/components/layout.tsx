import { useEffect } from "react";
import { useNavigate, useLocation, Outlet } from "react-router-dom";
import { useTheme } from "next-themes";
import {
  Cat,
  Moon,
  Sun,
  Monitor,
  LogOut,
  MessageSquare,
  Settings,
  Brain,
  Users,
  Folder,
  ChevronLeft,
  ChevronRight,
  type LucideIcon,
  Github,
  Plug,
  Radio,
  Clock,
  Box,
  BarChart3,
  Sparkles,
  Network,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { authService } from "@/services/auth";
import { userService } from "@/services/user";
import { useUserStore } from "@/stores/userStore";
import { useAppStore } from "@/stores/appStore";

const ThemeIcon: React.FC<{ theme: string | undefined }> = ({ theme }) => {
  if (theme === "system") return <Monitor className="h-[1.2rem] w-[1.2rem]" />;
  if (theme === "light") return <Sun className="h-[1.2rem] w-[1.2rem]" />;
  return <Moon className="h-[1.2rem] w-[1.2rem]" />;
};

function ThemeToggle() {
  const { theme, setTheme } = useTheme();

  const cycleTheme = () => {
    if (theme === "system") {
      setTheme("light");
    } else if (theme === "light") {
      setTheme("dark");
    } else {
      setTheme("system");
    }
  };

  return (
    <Button variant="ghost" size="icon" onClick={cycleTheme}>
      <ThemeIcon theme={theme} />
      <span className="sr-only">切换主题</span>
    </Button>
  );
}

function UserMenu() {
  const navigate = useNavigate();
  const user = useUserStore((state) => state.user);
  const clearUser = useUserStore((state) => state.clearUser);

  const handleLogout = () => {
    authService.logout();
    clearUser();
    navigate("/login");
  };

  const displayName = user?.displayUsername || user?.username || "用户";
  const avatarUrl = user?.avatarUrl;
  const initials = (user?.username || "U").charAt(0).toUpperCase();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" className="relative h-8 w-8 rounded-full">
          <Avatar className="h-8 w-8">
            {avatarUrl ? (
              <AvatarImage src={avatarUrl} alt={displayName} />
            ) : null}
            <AvatarFallback>{initials}</AvatarFallback>
          </Avatar>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-56" align="end" forceMount>
        <DropdownMenuLabel className="font-normal">
          <div className="flex flex-col space-y-1">
            <p className="text-sm font-medium leading-none">{displayName}</p>
            <p className="text-xs leading-none text-muted-foreground">
              {user?.username}
            </p>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={handleLogout}>
          <LogOut className="mr-2 h-4 w-4" />
          退出
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

interface NavItem {
  path: string;
  label: string;
  icon: LucideIcon;
}

const navItems: NavItem[] = [
  { path: "/chat", label: "聊天", icon: MessageSquare },
  { path: "/channel", label: "频道", icon: Radio },
  { path: "/conversation", label: "会话", icon: Users },
  { path: "/scheduled", label: "定时任务", icon: Clock },
  { path: "/workspace", label: "工作区", icon: Folder },
  { path: "/agent", label: "智能体", icon: Brain },
  { path: "/agent-team", label: "智能体团队", icon: Network },
  { path: "/mcp", label: "MCP", icon: Plug },
  { path: "/skill", label: "技能", icon: Sparkles },
  { path: "/llm", label: "大语言模型", icon: Box },
  { path: "/statistics", label: "统计信息", icon: BarChart3 },
  { path: "/settings", label: "系统设置", icon: Settings },
];

interface SidebarProps {
  activePath: string;
  collapsed: boolean;
  onToggle: () => void;
}

function Sidebar({ activePath, collapsed, onToggle }: SidebarProps) {
  const navigate = useNavigate();

  return (
    <div
      className={`border-r bg-muted/30 flex flex-col transition-all duration-300 ${
        collapsed ? "w-16" : "w-64"
      }`}
    >
      <div className="p-4 border-b flex items-center justify-between">
        {!collapsed && (
          <div className="flex items-center gap-2">
            <Cat className="h-6 w-6" />
            <span className="font-bold">MeowClaw</span>
          </div>
        )}
        {collapsed && <Cat className="h-6 w-6 mx-auto" />}
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8"
          onClick={onToggle}
        >
          {collapsed ? (
            <ChevronRight className="h-4 w-4" />
          ) : (
            <ChevronLeft className="h-4 w-4" />
          )}
        </Button>
      </div>
      <nav className="flex-1 p-2 space-y-1">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive =
            activePath === item.path ||
            (item.path === "/chat" && activePath.startsWith("/chat/"));
          return (
            <Button
              key={item.path}
              variant={isActive ? "secondary" : "ghost"}
              className={`w-full gap-3 ${
                collapsed ? "justify-center px-2" : "justify-start px-3"
              }`}
              onClick={() => navigate(item.path)}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {!collapsed && <span>{item.label}</span>}
            </Button>
          );
        })}
      </nav>
    </div>
  );
}

interface HeaderProps {
  onSettingsClick: () => void;
}

function Header({ onSettingsClick }: HeaderProps) {
  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/60">
      <div className="container flex h-14 items-center justify-end gap-2 pr-4">
        <ThemeToggle />
        <Button variant="ghost" size="icon" onClick={onSettingsClick}>
          <Settings className="h-[1.2rem] w-[1.2rem]" />
          <span className="sr-only">系统设置</span>
        </Button>
        <Button
          variant="ghost"
          size="icon"
          onClick={() =>
            window.open("https://github.com/gacfox/meowclaw", "_blank")
          }
        >
          <Github className="h-[1.2rem] w-[1.2rem]" />
          <span className="sr-only">GitHub</span>
        </Button>
        <UserMenu />
      </div>
    </header>
  );
}

export function Layout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { sidebarOpen, setSidebarOpen } = useAppStore();
  const setUser = useUserStore((state) => state.setUser);

  useEffect(() => {
    userService
      .getCurrentUser()
      .then((res) => setUser(res.data))
      .catch(() => {});
  }, [setUser]);

  const activePath = "/" + (location.pathname.split("/")[1] || "chat");

  return (
    <div className="flex h-screen">
      <Sidebar
        activePath={activePath}
        collapsed={!sidebarOpen}
        onToggle={() => setSidebarOpen(!sidebarOpen)}
      />
      <div className="flex-1 flex flex-col">
        <Header onSettingsClick={() => navigate("/settings")} />
        <main className="flex-1 min-h-0 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
