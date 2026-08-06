import { type ReactNode } from 'react';
import { useAuth } from './authContext';
import { LoginPage } from './LoginPage';

/** Gate for the whole application: without a session the user sees the sign-in screen instead. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();

  if (!auth.isAuthenticated) {
    return <LoginPage />;
  }

  return <>{children}</>;
}
