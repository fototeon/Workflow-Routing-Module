import type { ReactNode } from 'react';
import { Alert, Box, Button } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { useAuth } from './authContext';
import { hasAnyRole, type Role } from './authConfig';

export function RequireRole({ allow, children }: { allow: Role[]; children: ReactNode }) {
  const auth = useAuth();
  const navigate = useNavigate();

  if (!hasAnyRole(auth.accessToken, allow)) {
    return (
      <Box sx={{ p: 4 }}>
        <Alert
          severity="warning"
          action={
            <Button color="inherit" size="small" onClick={() => navigate('/processes', { replace: true })}>
              К процессам
            </Button>
          }
        >
          У вас нет прав для просмотра этой страницы.
        </Alert>
      </Box>
    );
  }

  return <>{children}</>;
}
