import { useAuthStore } from "@/stores/authStore";
import { toast } from "sonner";

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

class RequestClient {
  async request<T>(
    endpoint: string,
    options?: RequestInit,
  ): Promise<ApiResponse<T>> {
    const headers: Record<string, string> = {
      ...((options?.headers as Record<string, string>) || {}),
    };

    const body = options?.body;
    if (!(body instanceof FormData)) {
      headers["Content-Type"] = "application/json";
    }

    const token = useAuthStore.getState().token;
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    const response = await fetch(endpoint, {
      ...options,
      headers,
    });

    const data = await response.json();

    if (data.code === 401) {
      useAuthStore.getState().clearToken();
      window.location.href = "/login";
      throw new Error("登录状态已失效");
    }

    if (!response.ok) {
      const errorMessage = data.message || `请求失败 (${response.status})`;
      toast.error(errorMessage);
      throw new Error(errorMessage);
    }

    return data;
  }

  setToken(token: string) {
    useAuthStore.getState().setToken(token);
  }

  clearToken() {
    useAuthStore.getState().clearToken();
  }

  getToken() {
    return useAuthStore.getState().token;
  }
}

export const request = new RequestClient();
