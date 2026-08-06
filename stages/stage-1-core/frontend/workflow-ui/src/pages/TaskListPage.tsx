import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
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
  TableRow,
  TextField,
} from '@mui/material';
import { completeTask, searchTasks } from '../api/tasks';
import type { TaskInstance, TaskInstanceStatus } from '../api/types';
import { StatusChip } from '../components/StatusChip';
import { TASK_STATUS_LABELS } from '../statusLabels';

const STATUS_OPTIONS: TaskInstanceStatus[] = ['CREATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'];

export function TaskListPage() {
  // Opened from a process row, the list narrows to that case's tasks (?processInstanceId=...).
  const [searchParams, setSearchParams] = useSearchParams();
  const processInstanceId = searchParams.get('processInstanceId') ?? undefined;
  const [tasks, setTasks] = useState<TaskInstance[]>([]);
  const [status, setStatus] = useState<TaskInstanceStatus | ''>('');
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const page = await searchTasks({ status: status || undefined, processInstanceId, size: 50 });
      setTasks(page.content);
      setError(null);
    } catch {
      setError('Не удалось загрузить задачи. Проверьте, что workflow-service запущен.');
    }
  }, [status, processInstanceId]);

  useEffect(() => {
    reload();
  }, [reload]);

  return (
    <Box>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
        <TextField
          select
          size="small"
          label="Статус"
          value={status}
          onChange={(e) => setStatus(e.target.value as TaskInstanceStatus | '')}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Любой</MenuItem>
          {STATUS_OPTIONS.map((s) => (
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
              <TableCell align="right">Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {tasks.map((task) => (
              <TableRow key={task.id}>
                <TableCell>{task.stepCode}</TableCell>
                <TableCell>{task.assigneeRole ?? '—'}</TableCell>
                <TableCell>{task.assigneeId ?? '—'}</TableCell>
                <TableCell>
                  <StatusChip
                    label={TASK_STATUS_LABELS[task.status].label}
                    tone={TASK_STATUS_LABELS[task.status].tone}
                  />
                </TableCell>
                <TableCell align="right">
                  {(task.status === 'CREATED' || task.status === 'IN_PROGRESS') && (
                    <Button
                      size="small"
                      variant="outlined"
                      onClick={async () => {
                        await completeTask(task.id);
                        reload();
                      }}
                    >
                      Выполнить
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {tasks.length === 0 && (
              <TableRow>
                <TableCell colSpan={5}>Задач пока нет.</TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}
