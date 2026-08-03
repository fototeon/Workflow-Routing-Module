import { useState, type FormEvent } from 'react';
import { Alert, Avatar, Box, Button, CircularProgress, Paper, TextField, Typography } from '@mui/material';
import { useAuth } from './authContext';
import { colors } from '../colors';

/** The application's own sign-in screen: credentials go to Keycloak and come back as a token. */
export function LoginPage() {
  const auth = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await auth.login(username.trim(), password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Не удалось выполнить вход.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '100vh', p: 2 }}>
      <Paper
        component="form"
        onSubmit={submit}
        sx={{
          width: '100%',
          maxWidth: 400,
          p: 4,
          display: 'flex',
          flexDirection: 'column',
          gap: 2.5,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Avatar
            sx={{
              width: 44,
              height: 44,
              fontWeight: 700,
              background: `linear-gradient(135deg, ${colors.accent.logoGradientFrom}, ${colors.accent.logoGradientTo})`,
            }}
          >
            W
          </Avatar>
          <Box>
            <Typography variant="h6" sx={{ color: colors.text.heading, fontWeight: 700, lineHeight: 1.2 }}>
              Workflow Модуль
            </Typography>
            <Typography variant="caption" sx={{ color: colors.text.caption }}>
              TZ-02-WORKFLOW
            </Typography>
          </Box>
        </Box>

        <Typography variant="body2" sx={{ color: colors.text.secondary }}>
          Введите учётные данные — вход выполняется через корпоративный каталог пользователей.
        </Typography>

        <TextField
          label="Логин"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          autoFocus
        />
        <TextField
          label="Пароль"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="current-password"
        />

        {error && <Alert severity="error">{error}</Alert>}

        <Button
          type="submit"
          variant="contained"
          disabled={submitting || !username.trim() || !password}
          startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : undefined}
        >
          Войти
        </Button>
      </Paper>
    </Box>
  );
}
