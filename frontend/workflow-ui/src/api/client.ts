import axios, { type InternalAxiosRequestConfig } from 'axios';
import { ensureFreshToken, refreshSession } from '../auth/authSession';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
});

/** Marks a request that already went through one refresh-and-retry cycle. */
type RetriableConfig = InternalAxiosRequestConfig & { retriedAfterRefresh?: boolean };

apiClient.interceptors.request.use(async (config) => {
  const token = await ensureFreshToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const config = error.config as RetriableConfig | undefined;
    // A token can still be rejected between refreshes (revoked session, restarted Keycloak) —
    // refresh once and replay the request; a second failure surfaces to the caller.
    if (error.response?.status === 401 && config && !config.retriedAfterRefresh) {
      config.retriedAfterRefresh = true;
      const session = await refreshSession();
      if (session) {
        config.headers.Authorization = `Bearer ${session.accessToken}`;
        return apiClient.request(config);
      }
    }
    return Promise.reject(error);
  },
);

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
