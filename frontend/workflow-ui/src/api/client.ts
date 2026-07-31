import axios from 'axios';
import { User } from 'oidc-client-ts';
import { KEYCLOAK_AUTHORITY, KEYCLOAK_CLIENT_ID } from '../auth/authConfig';

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
});

function getStoredUser(): User | null {
  const key = `oidc.user:${KEYCLOAK_AUTHORITY}:${KEYCLOAK_CLIENT_ID}`;
  const raw = sessionStorage.getItem(key);
  if (!raw) {
    return null;
  }
  try {
    return User.fromStorageString(raw);
  } catch {
    return null;
  }
}

apiClient.interceptors.request.use((config) => {
  const user = getStoredUser();
  if (user?.access_token) {
    config.headers.Authorization = `Bearer ${user.access_token}`;
  }
  return config;
});

export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}
