export interface ApiResult<T = unknown> {
  code: string;
  message: string;
  data: T;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  current: number;
  pageSize: number;
}
