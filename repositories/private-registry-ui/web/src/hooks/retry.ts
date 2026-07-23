import { ApiError } from "../api/client";

export const queryRetry = (failureCount: number, error: Error) =>
  error instanceof ApiError && error.status >= 500 && failureCount < 1;
