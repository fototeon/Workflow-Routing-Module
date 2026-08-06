import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import { cancelProcessInstance, searchProcessInstances, startProcessInstance } from '../api/processInstances';
import { listPublishedProcessDefinitions } from '../api/processDefinitions';
import type { ProcessDefinition, ProcessInstance, ProcessInstanceStatus } from '../api/types';
import { StatusChip } from '../components/StatusChip';
import { PROCESS_STATUS_LABELS } from '../statusLabels';

const STATUS_OPTIONS: ProcessInstanceStatus[] = ['NOT_STARTED', 'RUNNING', 'COMPLETED', 'CANCELLED'];

export function ProcessListPage() {
  const navigate = useNavigate();
  const [instances, setInstances] = useState<ProcessInstance[]>([]);
  const [definitions, setDefinitions] = useState<ProcessDefinition[]>([]);
  const [status, setStatus] = useState<ProcessInstanceStatus | ''>('');
  const [businessKeyFilter, setBusinessKeyFilter] = useState('');
  const [error, setError] = useState<string | null>(null);

  const [startOpen, setStartOpen] = useState(false);
  const [definitionId, setDefinitionId] = useState('');
  const [businessKey, setBusinessKey] = useState('');
  const [attributes, setAttributes] = useState('{"requestType":"COMPLEX"}');
  const [startError, setStartError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const page = await searchProcessInstances({
        status: status || undefined,
        businessKey: businessKeyFilter || undefined,
        size: 50,
      });
      setInstances(page.content);
      setError(null);
    } catch {
      setError('Не удалось загрузить процессы. Проверьте, что workflow-service запущен.');
    }
  }, [status, businessKeyFilter]);

  useEffect(() => {
    reload();
  }, [reload]);

  useEffect(() => {
    listPublishedProcessDefinitions().then(setDefinitions).catch(() => setDefinitions([]));
  }, []);

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <TextField
          select
          size="small"
          label="Статус"
          value={status}
          onChange={(e) => setStatus(e.target.value as ProcessInstanceStatus | '')}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Любой</MenuItem>
          {STATUS_OPTIONS.map((s) => (
            <MenuItem key={s} value={s}>
              {PROCESS_STATUS_LABELS[s].label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          label="Поиск по бизнес-ключу"
          value={businessKeyFilter}
          onChange={(e) => setBusinessKeyFilter(e.target.value)}
        />
        <Box sx={{ flexGrow: 1 }} />
        <Button variant="contained" onClick={() => setStartOpen(true)}>
          Запустить процесс
        </Button>
      </Stack>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Бизнес-ключ</TableCell>
              <TableCell>Статус</TableCell>
              <TableCell>Текущий шаг</TableCell>
              <TableCell>Запущен</TableCell>
              <TableCell align="right">Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {instances.map((instance) => (
              <TableRow key={instance.id}>
                <TableCell>{instance.businessKey}</TableCell>
                <TableCell>
                  <StatusChip
                    label={PROCESS_STATUS_LABELS[instance.status].label}
                    tone={PROCESS_STATUS_LABELS[instance.status].tone}
                  />
                </TableCell>
                <TableCell>{instance.currentStepCode ?? '—'}</TableCell>
                <TableCell>{instance.startedAt ? new Date(instance.startedAt).toLocaleString('ru-RU') : '—'}</TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/tasks?processInstanceId=${instance.id}`)}>
                    Задачи
                  </Button>
                  {instance.status === 'RUNNING' && (
                    <Button
                      size="small"
                      color="warning"
                      onClick={async () => {
                        await cancelProcessInstance(instance.id);
                        reload();
                      }}
                    >
                      Отменить
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {instances.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>Процессов пока нет.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={startOpen} onClose={() => setStartOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Запуск процесса</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {startError && <Alert severity="error">{startError}</Alert>}
            <TextField
              select
              label="Шаблон процесса"
              value={definitionId}
              onChange={(e) => setDefinitionId(e.target.value)}
            >
              {definitions.map((def) => (
                <MenuItem key={def.id} value={def.id}>
                  {def.code} v{def.version} — {def.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField label="Бизнес-ключ" value={businessKey} onChange={(e) => setBusinessKey(e.target.value)} />
            <TextField
              label="Атрибуты (JSON)"
              multiline
              minRows={3}
              value={attributes}
              onChange={(e) => setAttributes(e.target.value)}
              helperText="По этим атрибутам правила маршрутизации выбирают первый шаг"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStartOpen(false)}>Отмена</Button>
          <Button
            variant="contained"
            disabled={!definitionId || !businessKey.trim()}
            onClick={async () => {
              try {
                await startProcessInstance({
                  processDefinitionId: definitionId,
                  businessKey,
                  attributes: JSON.parse(attributes || '{}'),
                });
                setStartOpen(false);
                setStartError(null);
                setBusinessKey('');
                reload();
              } catch {
                setStartError('Не удалось запустить процесс. Проверьте шаблон и атрибуты.');
              }
            }}
          >
            Запустить
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
