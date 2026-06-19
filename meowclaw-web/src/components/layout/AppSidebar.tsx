import { Link, useLocation } from "react-router-dom";
import { MessageSquare, Cpu, Sparkles, Clock, Plug, Package, BarChart3 } from "lucide-react";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  SidebarRail,
} from "@/components/ui/sidebar";

export function AppSidebar() {
  const location = useLocation();

  return (
    <Sidebar collapsible="icon">
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" asChild>
              <Link to="/">
                <img src="/favicon.svg" alt="MeowClaw" className="size-8 rounded-lg" />
                <div className="flex flex-col gap-0.5 leading-none">
                  <span className="font-semibold">MeowClaw</span>
                </div>
              </Link>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupContent>
            <SidebarMenu>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="对话" asChild isActive={location.pathname === "/"}>
                  <Link to="/">
                    <MessageSquare />
                    <span>对话</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="LLM 配置" asChild isActive={location.pathname === "/llm"}>
                  <Link to="/llm">
                    <Cpu />
                    <span>LLM 配置</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="智能体" asChild isActive={location.pathname === "/agent"}>
                  <Link to="/agent">
                    <Sparkles />
                    <span>智能体</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="定时任务" asChild isActive={location.pathname === "/scheduled-task"}>
                  <Link to="/scheduled-task">
                    <Clock />
                    <span>定时任务</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="MCP 服务" asChild isActive={location.pathname === "/mcp-service"}>
                  <Link to="/mcp-service">
                    <Plug />
                    <span>MCP 服务</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="SKILL" asChild isActive={location.pathname === "/skill"}>
                  <Link to="/skill">
                    <Package />
                    <span>SKILL</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
              <SidebarMenuItem>
                <SidebarMenuButton tooltip="tokens 统计" asChild isActive={location.pathname === "/tokens"}>
                  <Link to="/tokens">
                    <BarChart3 />
                    <span>tokens 统计</span>
                  </Link>
                </SidebarMenuButton>
              </SidebarMenuItem>
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
      <SidebarRail />
    </Sidebar>
  );
}
