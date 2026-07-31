import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { hasAnyRole, type Role } from './authConfig';

/** Inline conditional rendering for role-gated UI elements (buttons, actions) — renders nothing when disallowed. */
export function RoleGate({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const auth = useAuth();
  return hasAnyRole(auth.user, allow) ? <>{children}</> : null;
}
