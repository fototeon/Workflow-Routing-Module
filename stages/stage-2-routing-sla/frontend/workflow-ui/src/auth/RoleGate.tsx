import type { ReactNode } from 'react';
import { useAuth } from './authContext';
import { hasAnyRole, type Role } from './authConfig';

/** Inline conditional rendering for role-gated UI elements (buttons, actions) — renders nothing when disallowed. */
export function RoleGate({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const auth = useAuth();
  return hasAnyRole(auth.accessToken, allow) ? <>{children}</> : null;
}
