import { useState } from "react";
import { Outlet } from "react-router-dom";
import { AppSidebar } from "./AppSidebar";
import { AppHeader } from "./AppHeader";
import { SidebarInset, SidebarProvider } from "@/components/ui/sidebar";

const SIDEBAR_OPEN_STORAGE_KEY = "meowclaw_sidebar_open";

function readSidebarOpen(): boolean {
  try {
    const stored = localStorage.getItem(SIDEBAR_OPEN_STORAGE_KEY);
    return stored == null ? true : stored === "true";
  } catch {
    return true;
  }
}

export function MainLayout() {
  const [sidebarOpen, setSidebarOpen] = useState(readSidebarOpen);

  const handleOpenChange = (open: boolean) => {
    setSidebarOpen(open);
    try {
      localStorage.setItem(SIDEBAR_OPEN_STORAGE_KEY, String(open));
    } catch { /* 存储不可用时仅保持当前页面状态 */ }
  };

  return (
    <SidebarProvider open={sidebarOpen} onOpenChange={handleOpenChange}>
      <AppSidebar />
      <SidebarInset>
        <AppHeader />
        <div className="flex-1 p-6">
          <Outlet />
        </div>
      </SidebarInset>
    </SidebarProvider>
  );
}
