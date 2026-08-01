import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { completeTask, reassignTask, searchTasks } from '../api/tasks';
import type { TaskInstance, TaskInstanceStatus } from '../api/types';
import { RoleGate } from '../auth/RoleGate';
import { ROLES } from '../auth/authConfig';
import { StatusChip } from '../components/StatusChip';
import { TASK_STATUS_LABELS } from '../statusLabels';
import { colors } from '../colors';

const STATUSES: TaskInstanceStatus[] = ['CREATED', 'IN_PROGRESS', 'WAITING', 'OVERDUE', 'COMPLETED', 'CANCELLED'];

export function TaskListPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<TaskInstance[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<TaskInstanceStatus | ''>('');
  const [reassignTarget, setReassignTarget] = useState<TaskInstance | null>(null);
  const [reassignTo, setReassignTo] = useState('');
  const [reassignReason, setReassignReason] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let cancelled = false;
    // newest first, so a freshly created task is always at the top of the list
    searchTasks({ page, size, status: status || undefined, sort: 'createdAt,desc' }).then((result) => {
      if (cancelled) return;
      setRows(result.content);
      setTotalElements(result.totalElements);
    });
    return () => {
      cancelled = true;
    };
  }, [page, size, status, reloadKey]);

  return (
    <Box>
      <Box sx={{ display: 'flex', gap: 2, mb: 2 }}>
        <TextField
          select
          label="Статус"
          size="small"
          value={status}
          onChange={(e) => {
            setPage(0);
            setStatus(e.target.value as TaskInstanceStatus | '');
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">Все статусы</MenuItem>
          {STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {TASK_STATUS_LABELS[s].label}
            </MenuItem>
          ))}
        </TextField>
      </Box>
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Шаг</TableCell>
              <TableCell>Статус</TableCell>
              <TableCell>Исполнитель</TableCell>
              <TableCell>Срок</TableCell>
              <TableCell align="right">Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover>
                <TableCell sx={{ cursor: 'pointer', color: colors.text.body }} onClick={() => navigate(`/processes/${row.processInstanceId}`)}>
                  {row.name}
                </TableCell>
                <TableCell>
                  <StatusChip label={TASK_STATUS_LABELS[row.status].label} tone={TASK_STATUS_LABELS[row.status].tone} />
                </TableCell>
                <TableCell>{row.assigneeId ?? row.assigneeRole ?? '—'}</TableCell>
                <TableCell sx={{ color: colors.text.caption }}>
                  {row.dueAt ? new Date(row.dueAt).toLocaleString('ru-RU') : '—'}
                </TableCell>
                <TableCell align="right">
                  {!['COMPLETED', 'CANCELLED'].includes(row.status) && (
                    <>
                      <Button
                        size="small"
                        onClick={async () => {
                          await completeTask(row.id);
                          setReloadKey((k) => k + 1);
                        }}
                      >
                        Выполнить
                      </Button>
                      <RoleGate allow={[ROLES.MANAGER, ROLES.ADMIN]}>
                        <Button size="small" onClick={() => setReassignTarget(row)}>
                          Переназначить
                        </Button>
                      </RoleGate>
                    </>
                  )}
                </TableCell>
              </TableRow>
            ))}
            {rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ color: colors.text.secondary }}>
                  Задачи не найдены.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={totalElements}
          page={page}
          onPageChange={(_, newPage) => setPage(newPage)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => {
            setSize(parseInt(e.target.value, 10));
            setPage(0);
          }}
          labelRowsPerPage="Строк на странице"
        />
      </TableContainer>

      <Dialog open={!!reassignTarget} onClose={() => setReassignTarget(null)} fullWidth maxWidth="sm">
        <DialogTitle>Переназначить задачу</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 1 }}>
          <TextField
            label="Новый исполнитель (id пользователя)"
            value={reassignTo}
            onChange={(e) => setReassignTo(e.target.value)}
            autoFocus
          />
          <TextField
            label="Причина"
            multiline
            minRows={2}
            value={reassignReason}
            onChange={(e) => setReassignReason(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReassignTarget(null)}>Закрыть</Button>
          <Button
            disabled={!reassignTo.trim() || !reassignReason.trim()}
            onClick={async () => {
              if (!reassignTarget) return;
              await reassignTask(reassignTarget.id, reassignTo, reassignReason);
              setReassignTarget(null);
              setReassignTo('');
              setReassignReason('');
              setReloadKey((k) => k + 1);
            }}
          >
            Подтвердить
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
