import { create } from "zustand";
import type { UserDTO } from "@/types";
import { getToken } from "@/services/request";
import { getCurrentUser, login as apiLogin, logout as apiLogout } from "@/services/auth";

interface AuthState {
  user: UserDTO | null;
  loading: boolean;
  setUser: (user: UserDTO) => void;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  fetchUser: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  loading: true,

  setUser: (user) => set({ user }),

  login: async (username, password) => {
    const user = await apiLogin(username, password);
    set({ user });
  },

  logout: async () => {
    await apiLogout();
    set({ user: null });
  },

  fetchUser: async () => {
    if (!getToken()) {
      set({ user: null, loading: false });
      return;
    }
    try {
      const user = await getCurrentUser();
      set({ user, loading: false });
    } catch {
      set({ user: null, loading: false });
    }
  },
}));
