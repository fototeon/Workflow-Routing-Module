import { KEYCLOAK_AUTHORITY, KEYCLOAK_CLIENT_ID, decodeJwtPayload } from './authConfig';

/**
 * Token handling for the application's own login form: credentials are exchanged for tokens at
 * Keycloak's token endpoint (OAuth2 direct access grant), and the resulting access token is what
 * the API client sends and the UI reads roles from. The session lives in sessionStorage, so it
 * survives a page reload but not closing the tab.
 */

const TOKEN_ENDPOINT = `${KEYCLOAK_AUTHORITY}/protocol/openid-connect/token`;
const LOGOUT_ENDPOINT = `${KEYCLOAK_AUTHORITY}/protocol/openid-connect/logout`;
const STORAGE_KEY = 'workflow.session';
/** Refresh this long before the access token actually expires, so in-flight requests stay valid. */
const REFRESH_SKEW_MS = 30_000;

export interface Session {
  accessToken: string;
  refreshToken: string | null;
  /** Epoch millis at which the access token expires. */
  expiresAt: number;
  username: string;
}

export class AuthError extends Error {}

type Listener = (session: Session | null) => void;

const listeners = new Set<Listener>();
let session: Session | null = readStoredSession();
let refreshInFlight: Promise<Session | null> | null = null;

function readStoredSession(): Session | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(raw) as Session;
    return parsed.accessToken ? parsed : null;
  } catch {
    return null;
  }
}

function setSession(next: Session | null) {
  session = next;
  if (next) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next));
  } else {
    sessionStorage.removeItem(STORAGE_KEY);
  }
  listeners.forEach((listener) => listener(next));
}

export function getSession(): Session | null {
  return session;
}

export function getAccessToken(): string | null {
  return session?.accessToken ?? null;
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

async function requestTokens(body: Record<string, string>): Promise<Session> {
  let response: Response;
  try {
    response = await fetch(TOKEN_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: KEYCLOAK_CLIENT_ID, ...body }),
    });
  } catch {
    throw new AuthError('Не удалось связаться с сервером авторизации. Проверьте, что Keycloak запущен.');
  }

  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new AuthError(describeError(payload));
  }

  const claims = decodeJwtPayload(payload.access_token);
  return {
    accessToken: payload.access_token,
    refreshToken: payload.refresh_token ?? null,
    expiresAt: Date.now() + (payload.expires_in ?? 60) * 1000,
    username: (claims?.preferred_username as string) ?? '',
  };
}

function describeError(payload: { error?: string; error_description?: string }): string {
  if (payload.error === 'invalid_grant') {
    return 'Неверный логин или пароль.';
  }
  if (payload.error === 'invalid_client' || payload.error === 'unauthorized_client') {
    return 'Клиент авторизации настроен неверно: для него не разрешён прямой вход по логину и паролю.';
  }
  return payload.error_description ?? payload.error ?? 'Не удалось выполнить вход.';
}

/** Exchanges credentials for tokens and starts the session. */
export async function login(username: string, password: string): Promise<Session> {
  const next = await requestTokens({
    grant_type: 'password',
    scope: 'openid profile email',
    username,
    password,
  });
  setSession(next);
  return next;
}

/** Drops the session locally and, best-effort, revokes the refresh token at Keycloak. */
export async function logout(): Promise<void> {
  const refreshToken = session?.refreshToken;
  setSession(null);
  if (!refreshToken) {
    return;
  }
  try {
    await fetch(LOGOUT_ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ client_id: KEYCLOAK_CLIENT_ID, refresh_token: refreshToken }),
    });
  } catch {
    // The local session is already gone; a failed revoke must not block the user from logging out.
  }
}

/**
 * Exchanges the refresh token for a new access token. Concurrent callers share one request, and a
 * refusal from Keycloak (expired or revoked refresh token) ends the session.
 */
export async function refreshSession(): Promise<Session | null> {
  if (refreshInFlight) {
    return refreshInFlight;
  }
  const refreshToken = session?.refreshToken;
  if (!refreshToken) {
    setSession(null);
    return null;
  }

  refreshInFlight = requestTokens({ grant_type: 'refresh_token', refresh_token: refreshToken })
    .then((next) => {
      setSession(next);
      return next;
    })
    .catch(() => {
      setSession(null);
      return null;
    })
    .finally(() => {
      refreshInFlight = null;
    });

  return refreshInFlight;
}

/** Returns a token that is still valid for at least the refresh skew, refreshing it if needed. */
export async function ensureFreshToken(): Promise<string | null> {
  if (!session) {
    return null;
  }
  if (session.expiresAt - Date.now() > REFRESH_SKEW_MS) {
    return session.accessToken;
  }
  return (await refreshSession())?.accessToken ?? null;
}

/** Millis until the session should be refreshed, floored at zero. */
export function millisUntilRefresh(current: Session): number {
  return Math.max(current.expiresAt - Date.now() - REFRESH_SKEW_MS, 0);
}
