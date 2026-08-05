import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
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
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { completeTask, reassignTask, searchTasks } from '../api/tasks';
import { describeActionError, describeLoadError } from '../api/errors';
import { downloadCsv } from '../api/analytics';
import { SavedViews } from '../components/SavedViews';
import DownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
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
  const [loadError, setLoadError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [sort, setSort] = useState<{ field: string; direction: 'asc' | 'desc' }>({ field: 'createdAt', direction: 'desc' });
  const [selected, setSelected] = useState<string[]>([]);
  const [bulkBusy, setBulkBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoadError(null);
    searchTasks({ page, size, status: status || undefined, sort: `${sort.field},${sort.direction}` })
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
  }, [page, size, status, reloadKey, sort]);

  const selectableIds = rows.filter((row) => !['COMPLETED', 'CANCELLED'].includes(row.status)).map((row) => row.id);

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
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <SavedViews
          storageKey="workflow.views.tasks"
          currentFilters={{ status }}
          onApply={(filters) => {
            setPage(0);
            setStatus(filters.status);
          }}
        />
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <RoleGate allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
            <Button
              disabled={selected.length === 0 || bulkBusy}
              onClick={async () => {
                setBulkBusy(true);
                setActionError(null);
                try {
                  // Bulk action within the caller's rights (TZ §9): each task goes through the same
                  // endpoint as the single-row action, so the server re-checks every one of them.
                  for (const taskId of selected) {
                    await completeTask(taskId);
                  }
                  setSelected([]);
                } catch (err) {
                  setActionError(describeActionError(err, 'Не удалось выполнить часть задач.'));
                } finally {
                  setBulkBusy(false);
                  setReloadKey((k) => k + 1);
                }
              }}
            >
              Выполнить выбранные ({selected.length})
            </Button>
          </RoleGate>
          <RoleGate allow={[ROLES.ANALYST, ROLES.MANAGER, ROLES.ADMIN]}>
            <Button
              startIcon={<DownloadOutlinedIcon />}
              onClick={() => downloadCsv('/tasks/export', { status: status || undefined }, 'tasks.csv')}
            >
              Выгрузить CSV
            </Button>
          </RoleGate>
        </Stack>
      </Stack>
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
              <TableCell padding="checkbox">
                <Checkbox
                  indeterminate={selected.length > 0 && selected.length < selectableIds.length}
                  checked={selectableIds.length > 0 && selected.length === selectableIds.length}
                  onChange={(e) => setSelected(e.target.checked ? selectableIds : [])}
                  slotProps={{ input: { 'aria-label': 'Выбрать все задачи' } }}
                />
              </TableCell>
              {[
                { field: 'stepCode', label: 'Шаг' },
                { field: 'status', label: 'Статус' },
                { field: 'assigneeId', label: 'Исполнитель' },
                { field: 'dueAt', label: 'Срок' },
              ].map((column) => (
                <TableCell key={column.field} sortDirection={sort.field === column.field ? sort.direction : false}>
                  <TableSortLabel
                    active={sort.field === column.field}
                    direction={sort.field === column.field ? sort.direction : 'asc'}
                    onClick={() =>
                      setSort((current) =>
                        current.field === column.field
                          ? { field: column.field, direction: current.direction === 'asc' ? 'desc' : 'asc' }
                          : { field: column.field, direction: 'asc' },
                      )
                    }
                  >
                    {column.label}
                  </TableSortLabel>
                </TableCell>
              ))}
              <TableCell align="right">Действия</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover selected={selected.includes(row.id)}>
                <TableCell padding="checkbox">
                  <Checkbox
                    disabled={!selectableIds.includes(row.id)}
                    checked={selected.includes(row.id)}
                    onChange={(e) =>
                      setSelected((current) =>
                        e.target.checked ? [...current, row.id] : current.filter((id) => id !== row.id),
                      )
                    }
                    slotProps={{ input: { 'aria-label': `Выбрать задачу ${row.name}` } }}
                  />
                </TableCell>
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
                          setActionError(null);
                          try {
                            await completeTask(row.id);
                            setReloadKey((k) => k + 1);
                          } catch (err) {
                            setActionError(describeActionError(err, 'Не удалось выполнить задачу.'));
                          }
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
                <TableCell colSpan={6} align="center" sx={{ color: colors.text.secondary }}>
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
              setActionError(null);
              try {
                await reassignTask(reassignTarget.id, reassignTo, reassignReason);
                setReassignTarget(null);
                setReassignTo('');
                setReassignReason('');
                setReloadKey((k) => k + 1);
              } catch (err) {
                setActionError(describeActionError(err, 'Не удалось переназначить задачу.'));
              }
            }}
          >
            Подтвердить
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
