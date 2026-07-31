import { type ReactNode, useEffect } from 'react';
import { useAuth } from 'react-oidc-context';
import { Box, CircularProgress, Alert } from '@mui/material';

export function RequireAuth({ children }: { children: ReactNode }) {
  const auth = useAuth();

  useEffect(() => {
    if (!auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator) {
      auth.signinRedirect();
    }
  }, [auth]);

  if (auth.isLoading || (!auth.isAuthenticated && !auth.error)) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (auth.error) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert severity="error">Ошибка аутентификации: {auth.error.message}</Alert>
      </Box>
    );
  }

  return <>{children}</>;
}
