import { request } from "@/services/request";

export interface StatisticsOverviewDto {
  modelCount: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalMessages: number;
}

export interface DailyStatisticsDto {
  date: string;
  apiUrl: string;
  model: string;
  displayName: string;
  inputTokens: number;
  outputTokens: number;
  messageCount: number;
}

export interface ModelInfo {
  apiUrl: string;
  model: string;
  displayName: string;
}

export interface StatisticsParams {
  startTime?: number;
  endTime?: number;
  apiUrl?: string;
  model?: string;
}

export const statisticsService = {
  async getOverview(params?: StatisticsParams) {
    const queryParams = new URLSearchParams();
    if (params?.startTime) {
      queryParams.append("startTime", params.startTime.toString());
    }
    if (params?.endTime) {
      queryParams.append("endTime", params.endTime.toString());
    }
    const queryString = queryParams.toString();
    const url = queryString
      ? `/api/statistics/overview?${queryString}`
      : "/api/statistics/overview";
    return request.request<StatisticsOverviewDto>(url);
  },

  async getDailyStatistics(params?: StatisticsParams) {
    const queryParams = new URLSearchParams();
    if (params?.startTime) {
      queryParams.append("startTime", params.startTime.toString());
    }
    if (params?.endTime) {
      queryParams.append("endTime", params.endTime.toString());
    }
    if (params?.apiUrl) {
      queryParams.append("apiUrl", params.apiUrl);
    }
    if (params?.model) {
      queryParams.append("model", params.model);
    }
    const queryString = queryParams.toString();
    const url = queryString
      ? `/api/statistics/daily?${queryString}`
      : "/api/statistics/daily";
    return request.request<DailyStatisticsDto[]>(url);
  },

  async getAvailableModels() {
    return request.request<ModelInfo[]>("/api/statistics/models");
  },
};
