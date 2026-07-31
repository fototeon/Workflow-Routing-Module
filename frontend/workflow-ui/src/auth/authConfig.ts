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

export function getRoles(profile: Record<string, unknown> | undefined): Role[] {
  const realmAccess = profile?.realm_access as RealmAccessClaim | undefined;
  const roles = realmAccess?.roles ?? [];
  return roles.filter((role): role is Role => (Object.values(ROLES) as string[]).includes(role));
}

export function hasAnyRole(profile: Record<string, unknown> | undefined, allowed: Role[]): boolean {
  const roles = getRoles(profile);
  return roles.some((role) => allowed.includes(role));
}
