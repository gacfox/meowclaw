import { request } from "@/services/request";

export interface ChatRequestDto {
  conversationId: number;
  content: string;
}

export interface ChatStreamEvent {
  type: "content" | "tool_call" | "tool_result" | "finish" | "error";
  content: string;
  timestamp: number;
}

export const chatService = {
  async chatStream(data: ChatRequestDto): Promise<Response> {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };

    const token = request.getToken();
    if (token) {
      headers["Authorization"] = `Bearer ${token}`;
    }

    return fetch("/api/chat/stream", {
      method: "POST",
      headers,
      body: JSON.stringify(data),
    });
  },
};
