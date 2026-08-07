import { useEffect, useState } from 'react';
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
  TablePagination,
  TableRow,
  TableSortLabel,
  TextField,
  Typography,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { listPublishedProcessDefinitions } from '../api/processDefinitions';
import { searchProcessInstances, startProcessInstance } from '../api/processInstances';
import { describeActionError, describeLoadError } from '../api/errors';
import type { ProcessDefinition, ProcessInstance, ProcessInstanceStatus } from '../api/types';
import { RoleGate } from '../auth/RoleGate';
import { ROLES } from '../auth/authConfig';
import { StatusChip } from '../components/StatusChip';
import { PROCESS_STATUS_LABELS } from '../statusLabels';
import { colors } from '../colors';

const STATUSES: ProcessInstanceStatus[] = [
  'NOT_STARTED',
  'RUNNING',
  'WAITING_EXTERNAL',
  'OVERDUE',
  'COMPLETED',
  'CANCELLED',
];

export function ProcessListPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<ProcessInstance[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [status, setStatus] = useState<ProcessInstanceStatus | ''>('');
  const [businessKey, setBusinessKey] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [sort, setSort] = useState<{ field: string; direction: 'asc' | 'desc' }>({ field: 'createdAt', direction: 'desc' });
  const [reloadKey, setReloadKey] = useState(0);

  const [startOpen, setStartOpen] = useState(false);
  const [publishedDefinitions, setPublishedDefinitions] = useState<ProcessDefinition[]>([]);
  const [startDefinitionId, setStartDefinitionId] = useState('');
  const [startBusinessKey, setStartBusinessKey] = useState('');
  const [startAttributesJson, setStartAttributesJson] = useState('{\n  "requestType": "COMPLEX"\n}');
  const [startError, setStartError] = useState<string | null>(null);

  useEffect(() => {
    if (startOpen) {
      listPublishedProcessDefinitions().then(setPublishedDefinitions);
    }
  }, [startOpen]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    searchProcessInstances({
      page,
      size,
      status: status || undefined,
      businessKey: businessKey || undefined,
      sort: `${sort.field},${sort.direction}`,
    })
      .then((result) => {
        if (cancelled) return;
        setRows(result.content);
        setTotalElements(result.totalElements);
      })
      .catch((error) => {
        if (cancelled) return;
        setRows([]);
        setTotalElements(0);
        setLoadError(describeLoadError(error, 'процессы'));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [page, size, status, businessKey, reloadKey, sort]);

  return (
    <Box>
      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2, gap: 2, flexWrap: 'wrap' }}>
        <Box />
        <Stack direction="row" spacing={1}>
          <RoleGate allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
            <Button variant="contained" onClick={() => setStartOpen(true)}>
              Запустить процесс
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
            setStatus(e.target.value as ProcessInstanceStatus | '');
          }}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">Все статусы</MenuItem>
          {STATUSES.map((s) => (
            <MenuItem key={s} value={s}>
              {PROCESS_STATUS_LABELS[s].label}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          label="Поиск по бизнес-ключу"
          size="small"
          value={businessKey}
          onChange={(e) => {
            setPage(0);
            setBusinessKey(e.target.value);
          }}
        />
      </Box>
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              {[
                { field: 'businessKey', label: 'Бизнес-ключ' },
                { field: 'status', label: 'Статус' },
                { field: 'currentStepCode', label: 'Текущий шаг' },
                { field: 'startedAt', label: 'Начат' },
                { field: 'updatedAt', label: 'Обновлён' },
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
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row) => (
              <TableRow key={row.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/processes/${row.id}`)}>
                <TableCell sx={{ color: colors.status.businessKey, fontWeight: 600 }}>{row.businessKey}</TableCell>
                <TableCell>
                  <StatusChip label={PROCESS_STATUS_LABELS[row.status].label} tone={PROCESS_STATUS_LABELS[row.status].tone} />
                </TableCell>
                <TableCell>{row.currentStepCode ?? '—'}</TableCell>
                <TableCell sx={{ color: colors.text.caption }}>
                  {row.startedAt ? new Date(row.startedAt).toLocaleString('ru-RU') : '—'}
                </TableCell>
                <TableCell sx={{ color: colors.text.caption }}>{new Date(row.updatedAt).toLocaleString('ru-RU')}</TableCell>
              </TableRow>
            ))}
            {!loading && rows.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center" sx={{ color: colors.text.secondary }}>
                  Процессы не найдены.
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

      <Dialog open={startOpen} onClose={() => setStartOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Запустить новый процесс</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 3 }}>
          <TextField
            select
            label="Шаблон процесса"
            value={startDefinitionId}
            onChange={(e) => setStartDefinitionId(e.target.value)}
          >
            {publishedDefinitions.map((def) => (
              <MenuItem key={def.id} value={def.id}>
                {def.code} v{def.version}
              </MenuItem>
            ))}
            {publishedDefinitions.length === 0 && (
              <MenuItem disabled value="">
                Нет опубликованных шаблонов
              </MenuItem>
            )}
          </TextField>
          <TextField
            label="Бизнес-ключ"
            value={startBusinessKey}
            onChange={(e) => setStartBusinessKey(e.target.value)}
            helperText="Номер заявки или дела во внешней системе — по нему процесс ищут в реестре и связывают с событиями. Например: REQ-2026-001"
          />
          <TextField
            label="Атрибуты (JSON)"
            multiline
            minRows={4}
            value={startAttributesJson}
            onChange={(e) => setStartAttributesJson(e.target.value)}
            sx={{ fontFamily: 'monospace' }}
          />
          {startError && (
            <Typography color="error" variant="body2">
              {startError}
            </Typography>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStartOpen(false)}>Закрыть</Button>
          <Button
            variant="contained"
            disabled={!startDefinitionId || !startBusinessKey.trim()}
            onClick={async () => {
              setStartError(null);
              let attributes: Record<string, unknown>;
              try {
                attributes = JSON.parse(startAttributesJson);
              } catch {
                setStartError('Атрибуты должны быть корректным JSON.');
                return;
              }
              try {
                const created = await startProcessInstance({
                  processDefinitionId: startDefinitionId,
                  businessKey: startBusinessKey,
                  attributes,
                });
                setStartOpen(false);
                setStartBusinessKey('');
                setReloadKey((k) => k + 1);
                navigate(`/processes/${created.id}`);
              } catch (err) {
                setStartError(describeActionError(err, 'Не удалось запустить процесс.'));
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
