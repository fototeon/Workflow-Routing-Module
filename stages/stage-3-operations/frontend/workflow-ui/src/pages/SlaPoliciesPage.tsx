import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Checkbox,
  FormControlLabel,
  IconButton,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import { createSlaPolicy, listSlaPolicies } from '../api/slaPolicies';
import type { EscalationRule, SlaPolicy } from '../api/types';

export function SlaPoliciesPage() {
  const [policies, setPolicies] = useState<SlaPolicy[]>([]);
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(480);
  const [businessHoursOnly, setBusinessHoursOnly] = useState(true);
  const [escalationRules, setEscalationRules] = useState<EscalationRule[]>([{ afterPercent: 80, escalateToRole: 'MANAGER' }]);

  const load = () => listSlaPolicies().then(setPolicies);

  useEffect(() => {
    load();
  }, []);

  return (
    <Box>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={3}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
          <Typography variant="subtitle1" gutterBottom>
            Существующие политики
          </Typography>
          <List dense>
            {policies.map((p) => (
              <ListItem key={p.id}>
                <ListItemText
                  primary={`${p.name} (${p.code})`}
                  secondary={`${p.durationMinutes} мин, ${p.businessHoursOnly ? 'рабочие часы' : 'круглосуточно'}; эскалация: ${p.escalationRules
                    .map((r) => `${r.afterPercent}%→${r.escalateToRole}`)
                    .join(', ') || 'нет'}`}
                />
              </ListItem>
            ))}
            {policies.length === 0 && (
              <ListItem>
                <ListItemText primary="SLA политик пока нет." />
              </ListItem>
            )}
          </List>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2, flex: 1 }}>
          <Typography variant="subtitle1" gutterBottom>
            Новая политика
          </Typography>
          <Stack spacing={2}>
            <TextField size="small" label="Код" value={code} onChange={(e) => setCode(e.target.value)} />
            <TextField size="small" label="Название" value={name} onChange={(e) => setName(e.target.value)} />
            <TextField
              size="small"
              type="number"
              label="Длительность (минуты)"
              value={durationMinutes}
              onChange={(e) => setDurationMinutes(Number(e.target.value))}
            />
            <FormControlLabel
              control={<Checkbox checked={businessHoursOnly} onChange={(e) => setBusinessHoursOnly(e.target.checked)} />}
              label="Только рабочие часы (Пн–Пт, 09:00–18:00)"
            />

            <Typography variant="body2" color="text.secondary">
              Шаги эскалации
            </Typography>
            {escalationRules.map((rule, idx) => (
              <Stack direction="row" spacing={1} key={idx}>
                <TextField
                  size="small"
                  type="number"
                  label="После %"
                  value={rule.afterPercent}
                  onChange={(e) =>
                    setEscalationRules((prev) =>
                      prev.map((r, i) => (i === idx ? { ...r, afterPercent: Number(e.target.value) } : r)),
                    )
                  }
                  sx={{ width: 100 }}
                />
                <TextField
                  size="small"
                  select
                  label="Эскалировать на роль"
                  value={rule.escalateToRole}
                  onChange={(e) =>
                    setEscalationRules((prev) => prev.map((r, i) => (i === idx ? { ...r, escalateToRole: e.target.value } : r)))
                  }
                  sx={{ minWidth: 160 }}
                >
                  {['COORDINATOR', 'MANAGER', 'ADMIN'].map((role) => (
                    <MenuItem key={role} value={role}>
                      {role}
                    </MenuItem>
                  ))}
                </TextField>
                <IconButton onClick={() => setEscalationRules((prev) => prev.filter((_, i) => i !== idx))}>
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            ))}
            <Button
              startIcon={<AddIcon />}
              onClick={() => setEscalationRules((prev) => [...prev, { afterPercent: 100, escalateToRole: 'MANAGER' }])}
            >
              Добавить шаг эскалации
            </Button>

            <Button
              variant="contained"
              disabled={!code.trim() || !name.trim() || durationMinutes <= 0}
              onClick={async () => {
                await createSlaPolicy({ code, name, durationMinutes, businessHoursOnly, escalationRules });
                setCode('');
                setName('');
                setDurationMinutes(480);
                setBusinessHoursOnly(true);
                setEscalationRules([{ afterPercent: 80, escalateToRole: 'MANAGER' }]);
                load();
              }}
            >
              Создать политику
            </Button>
          </Stack>
        </Paper>
      </Stack>
    </Box>
  );
}
