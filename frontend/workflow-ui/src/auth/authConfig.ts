export const KEYCLOAK_AUTHORITY = import.meta.env.VITE_KEYCLOAK_AUTHORITY ?? 'http://localhost:8081/realms/workflow';
export const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'workflow-ui';

export const authConfig = {
  authority: KEYCLOAK_AUTHORITY,
  client_id: KEYCLOAK_CLIENT_ID,
  redirect_uri: window.location.origin,
  post_logout_redirect_uri: window.location.origin,
  scope: 'openid profile email',
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

export const ROLES = {
  ADMIN: 'ADMIN',
  MANAGER: 'MANAGER',
  COORDINATOR: 'COORDINATOR',
  ANALYST: 'ANALYST',
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

interface RealmAccessClaim {
  roles?: string[];
}

function decodeJwtPayload(token: string | undefined): Record<string, unknown> | undefined {
  const payload = token?.split('.')[1];
  if (!payload) {
    return undefined;
  }
  try {
    const binary = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
  } catch {
    return undefined;
  }
}

function realmRolesOf(claims: Record<string, unknown> | undefined): Role[] {
  const realmAccess = claims?.realm_access as RealmAccessClaim | undefined;
  const roles = realmAccess?.roles ?? [];
  return roles.filter((role): role is Role => (Object.values(ROLES) as string[]).includes(role));
}

/**
 * Keycloak's realm-role mapper writes `realm_access` into the access token, not the ID token, so
 * roles are read from the access token (the same claim the backend authorizes on) with the ID
 * token profile as a fallback for realms configured to include it there.
 */
export function getRoles(user: { access_token?: string; profile?: Record<string, unknown> } | null | undefined): Role[] {
  const fromAccessToken = realmRolesOf(decodeJwtPayload(user?.access_token));
  return fromAccessToken.length > 0 ? fromAccessToken : realmRolesOf(user?.profile);
}

export function hasAnyRole(
  user: { access_token?: string; profile?: Record<string, unknown> } | null | undefined,
  allowed: Role[],
): boolean {
  const roles = getRoles(user);
  return roles.some((role) => allowed.includes(role));
}
