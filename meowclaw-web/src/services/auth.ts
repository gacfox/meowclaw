import { request } from "@/services/request";

export interface InitStatus {
  initialized: boolean;
}

export interface LoginDto {
  username: string;
  password: string;
}

export interface InitDto {
  username: string;
  password: string;
}

export interface TokenDto {
  token: string;
}

export const authService = {
  async getInitStatus() {
    return request.request<InitStatus>("/api/auth/init/status");
  },

  async initSystem(data: InitDto) {
    return request.request<void>("/api/auth/init", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },

  async login(data: LoginDto) {
    const response = await request.request<TokenDto>("/api/auth/login", {
      method: "POST",
      body: JSON.stringify(data),
    });
    if (response.data?.token) {
      request.setToken(response.data.token);
    }
    return response;
  },

  logout() {
    request.clearToken();
  },
};
