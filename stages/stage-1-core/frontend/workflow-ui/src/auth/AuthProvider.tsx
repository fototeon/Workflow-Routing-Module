import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { AuthContext, type AuthState } from './authContext';
import {
  getSession,
  login as loginRequest,
  logout as logoutRequest,
  millisUntilRefresh,
  refreshSession,
  subscribe,
  type Session,
} from './authSession';

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(getSession);

  // The session also changes outside React (a 401 retry refreshing it, or a failed refresh ending it).
  useEffect(() => subscribe(setSession), []);

  // Keep the access token fresh while the tab stays open.
  useEffect(() => {
    if (!session) {
      return;
    }
    const timer = setTimeout(() => void refreshSession(), millisUntilRefresh(session));
    return () => clearTimeout(timer);
  }, [session]);

  const login = useCallback(async (username: string, password: string) => {
    await loginRequest(username, password);
  }, []);

  const logout = useCallback(async () => {
    await logoutRequest();
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      session,
      accessToken: session?.accessToken ?? null,
      username: session?.username ?? '',
      isAuthenticated: session !== null,
      login,
      logout,
    }),
    [session, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
