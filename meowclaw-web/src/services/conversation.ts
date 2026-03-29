import { request } from "@/services/request";

export interface ConversationDto {
  id?: number;
  agentConfigId: number;
  title: string;
}

export interface PageDto<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export const conversationService = {
  async list(params?: {
    page?: number;
    pageSize?: number;
    agentConfigId?: number;
  }) {
    const searchParams = new URLSearchParams();
    if (params?.page) searchParams.set("page", params.page.toString());
    if (params?.pageSize)
      searchParams.set("pageSize", params.pageSize.toString());
    if (params?.agentConfigId)
      searchParams.set("agentConfigId", params.agentConfigId.toString());
    const queryString = searchParams.toString();
    const url = queryString
      ? `/api/conversations?${queryString}`
      : "/api/conversations";
    return request.request<PageDto<ConversationDto>>(url);
  },

  async getById(id: number) {
    return request.request<ConversationDto>(`/api/conversations/${id}`);
  },

  async listMessages(id: number) {
    return request.request<
      {
        id: number;
        role: string;
        content: string;
        timestamp: number;
        inputTokens?: number;
        outputTokens?: number;
      }[]
    >(`/api/conversations/${id}/messages`);
  },

  async create(data: ConversationDto) {
    return request.request<ConversationDto>("/api/conversations", {
      method: "POST",
      body: JSON.stringify(data),
    });
  },

  async update(id: number, data: ConversationDto) {
    return request.request<ConversationDto>(`/api/conversations/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    });
  },

  async delete(id: number) {
    return request.request<void>(`/api/conversations/${id}`, {
      method: "DELETE",
    });
  },

  async generateTitle(id: number) {
    return request.request<ConversationDto>(
      `/api/conversations/${id}/generate-title`,
      {
        method: "POST",
      },
    );
  },

  async deleteMessagesAfter(conversationId: number, messageId: number) {
    return request.request<void>(
      `/api/conversations/${conversationId}/messages/after/${messageId}`,
      {
        method: "DELETE",
      },
    );
  },
};
