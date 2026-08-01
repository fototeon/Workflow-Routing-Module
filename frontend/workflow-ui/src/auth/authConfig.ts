export const KEYCLOAK_AUTHORITY = import.meta.env.VITE_KEYCLOAK_AUTHORITY ?? 'http://localhost:8081/realms/workflow';
export const KEYCLOAK_CLIENT_ID = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'workflow-ui';

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

/** Reads a JWT's payload without verifying it — the backend is what actually validates the token. */
export function decodeJwtPayload(token: string | null | undefined): Record<string, unknown> | undefined {
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

/** Realm roles live in the access token's `realm_access` claim — the same claim the backend authorizes on. */
export function getRoles(accessToken: string | null | undefined): Role[] {
  const realmAccess = decodeJwtPayload(accessToken)?.realm_access as RealmAccessClaim | undefined;
  const roles = realmAccess?.roles ?? [];
  return roles.filter((role): role is Role => (Object.values(ROLES) as string[]).includes(role));
}

export function hasAnyRole(accessToken: string | null | undefined, allowed: Role[]): boolean {
  const roles = getRoles(accessToken);
  return roles.some((role) => allowed.includes(role));
}
