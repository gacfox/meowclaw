import { create } from "zustand";
import { persist } from "zustand/middleware";

interface AppState {
  isInitialized: boolean | null;
  setInitialized: (value: boolean) => void;
  sidebarOpen: boolean;
  toggleSidebar: () => void;
  setSidebarOpen: (open: boolean) => void;
  lastSelectedAgentId: number | null;
  setLastSelectedAgentId: (id: number | null) => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set) => ({
      isInitialized: null,
      setInitialized: (value) => set({ isInitialized: value }),
      sidebarOpen: true,
      toggleSidebar: () => set((state) => ({ sidebarOpen: !state.sidebarOpen })),
      setSidebarOpen: (open) => set({ sidebarOpen: open }),
      lastSelectedAgentId: null,
      setLastSelectedAgentId: (id) => set({ lastSelectedAgentId: id }),
    }),
    {
      name: "meowclaw-app",
      partialize: (state) => ({ lastSelectedAgentId: state.lastSelectedAgentId }),
    },
  ),
);
