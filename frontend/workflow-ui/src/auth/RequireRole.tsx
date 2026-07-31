import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { Alert, Box } from '@mui/material';
import { hasAnyRole, type Role } from './authConfig';

export function RequireRole({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const auth = useAuth();

  if (!hasAnyRole(auth.user, allow)) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="warning">У вас нет прав для просмотра этой страницы.</Alert>
      </Box>
    );
  }

  return <>{children}</>;
}
