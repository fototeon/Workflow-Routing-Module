import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
} from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { completeTask, searchTasks } from '../api/tasks';
import { describeActionError, describeLoadError } from '../api/errors';
import type { TaskInstance, TaskInstanceStatus } from '../api/types';
import { RoleGate } from '../auth/RoleGate';
import { ROLES } from '../auth/authConfig';
import { StatusChip } from '../components/StatusChip';
import { TASK_STATUS_LABELS } from '../statusLabels';
import { colors } from '../colors';

const STATUSES: TaskInstanceStatus[] = ['CREATED', 'IN_PROGRESS', 'WAITING', 'OVERDUE', 'COMPLETED', 'CANCELLED'];

/** A deadline in the past is worth pointing out even before the SLA scan gets to the task. */
function isPastDue(task: TaskInstance): boolean {
  return task.dueAt !== null && task.status !== 'COMPLETED' && task.status !== 'CANCELLED'
    && new Date(task.dueAt).getTime() < Date.now();
}

export function TaskListPage() {
  const navigate = useNavigate();
  // Opened from a process card, the list narrows to that case's tasks (?processInstanceId=...).
  const [searchParams, setSearchParams] = useSearchParams();
  const processInstanceId = searchParams.get('processInstanceId') ?? undefined;
  const [rows, setRows] = useState<TaskInstance[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<TaskInstanceStatus | ''>('');
  const [reloadKey, setReloadKey] = useState(0);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    searchTasks({ page, size, status: status || undefined, processInstanceId })
      .then((result) => {
        if (cancelled) return;
        setRows(result.content);
        setTotalElements(result.totalElements);
      })
      .catch((error) => {
        if (cancelled) return;
        setRows([]);
        setTotalElements(0);
        setLoadError(describeLoadError(error, 'задачи'));
      });
    return () => {
      cancelled = true;
    };
  }, [page, size, status, processInstanceId, reloadKey]);

  return (
    <Box>
      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}
      {actionError && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>
          {actionError}
        </Alert>
      )}

      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          size="small"
          label="Статус"
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value as TaskInstanceStatus | '');
          }}
          sx={{ minWidth: 200 }}
        >
          <MenuItem value="">Любой</MenuItem>
          {STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {TASK_STATUS_LABELS[s].label}
            </MenuItem>
          ))}
        </TextField>
        {processInstanceId && (
          <Button size="small" onClick={() => setSearchParams({})}>
            Показаны задачи одной заявки — снять фильтр
          </Button>
        )}
      </Stack>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Шаг</TableCell>
              <TableCell>Роль</TableCell>
              <TableCell>Исполнитель</TableCell>
              <TableCell>Статус</TableCell>
              <TableCell>Срок</TableCell>
              <TableCell align="right">Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((task) => (
              <TableRow key={task.id} hover>
                <TableCell>{task.stepCode}</TableCell>
                <TableCell>{task.assigneeRole ?? '—'}</TableCell>
                <TableCell>{task.assigneeId ?? '—'}</TableCell>
                <TableCell>
                  <StatusChip
                    label={TASK_STATUS_LABELS[task.status].label}
                    tone={TASK_STATUS_LABELS[task.status].tone}
                  />
                </TableCell>
                <TableCell sx={{ color: isPastDue(task) ? '#F87171' : colors.text.body }}>
                  {task.dueAt ? new Date(task.dueAt).toLocaleString('ru-RU') : '—'}
                </TableCell>
                <TableCell align="right">
                  <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                    <Button size="small" onClick={() => navigate(`/processes/${task.processInstanceId}`)}>
                      Процесс
                    </Button>
                    <RoleGate allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
                      {task.status !== 'COMPLETED' && task.status !== 'CANCELLED' && (
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={async () => {
                            setActionError(null);
                            try {
                              await completeTask(task.id);
                              setReloadKey((key) => key + 1);
                            } catch (error) {
                              setActionError(describeActionError(error, 'Не удалось выполнить задачу.'));
                            }
                          }}
                        >
                          Выполнить
                        </Button>
                      )}
                    </RoleGate>
                  </Stack>
                </TableCell>
              </TableRow>
            ))}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>Задач пока нет.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          onPageChange={(_, next) => setPage(next)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => {
            setSize(Number(e.target.value));
            setPage(0);
          }}
          labelRowsPerPage="Строк на странице"
        />
      </TableContainer>
    </Box>
  );
}
