import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  List,
  ListItem,
  ListItemText,
  Paper,
  Step,
  StepLabel,
  Stepper,
  TextField,
  Typography,
} from '@mui/material';
import {
  cancelProcessInstance,
  getProcessInstance,
  getProcessInstanceEvents,
  resumeProcessInstance,
  startSubProcess,
  suspendProcessInstance,
} from '../api/processInstances';
import { listPublishedProcessDefinitions, listRoutingRules } from '../api/processDefinitions';
import type { ProcessDefinition, ProcessEventLogEntry, ProcessInstance, ProcessInstanceStatus, RoutingRule } from '../api/types';
import { ProcessMap } from '../components/ProcessMap';
import { MenuItem } from '@mui/material';
import { RoleGate } from '../auth/RoleGate';
import { ROLES } from '../auth/authConfig';
import { StatusChip } from '../components/StatusChip';
import { PROCESS_STATUS_LABELS } from '../statusLabels';
import { describeActionError } from '../api/errors';
import { colors } from '../colors';

const STEPS: ProcessInstanceStatus[] = ['NOT_STARTED', 'RUNNING', 'COMPLETED'];

function stepIndex(status: ProcessInstanceStatus): number {
  if (status === 'CANCELLED') return -1;
  if (['SUSPENDED', 'OVERDUE', 'WAITING_EXTERNAL'].includes(status)) return 1;
  const idx = STEPS.indexOf(status);
  return idx === -1 ? 0 : idx;
}

export function ProcessDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [instance, setInstance] = useState<ProcessInstance | null>(null);
  const [events, setEvents] = useState<ProcessEventLogEntry[]>([]);
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState('');
  const [suspendOpen, setSuspendOpen] = useState(false);
  const [suspendReason, setSuspendReason] = useState('');
  const [rules, setRules] = useState<RoutingRule[]>([]);
  const [subOpen, setSubOpen] = useState(false);
  const [subDefinitions, setSubDefinitions] = useState<ProcessDefinition[]>([]);
  const [subDefinitionId, setSubDefinitionId] = useState('');
  const [subBusinessKey, setSubBusinessKey] = useState('');
  const [subAttributes, setSubAttributes] = useState('{}');
  const [actionError, setActionError] = useState<string | null>(null);

  const load = useCallback(() => {
    if (!id) return;
    getProcessInstance(id).then((loaded) => {
      setInstance(loaded);
      // Route map comes from the template the instance runs on (REQ-02-008).
      listRoutingRules(loaded.processDefinitionId).then(setRules).catch(() => setRules([]));
    });
    getProcessInstanceEvents(id).then(setEvents);
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  if (!instance) {
    return <Typography sx={{ color: colors.text.secondary }}>Загрузка…</Typography>;
  }

  const canCancel = !['COMPLETED', 'CANCELLED'].includes(instance.status);
  const canSuspend = instance.status === 'RUNNING';
  const canResume = instance.status === 'SUSPENDED';

  return (
    <Box>
      <Button onClick={() => navigate('/processes')} sx={{ mb: 2, color: colors.text.secondary }}>
        ← Назад к процессам
      </Button>
      <Typography variant="h5" gutterBottom sx={{ color: colors.status.businessKey }}>
        {instance.businessKey}
      </Typography>
      <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 2 }}>
        <StatusChip label={PROCESS_STATUS_LABELS[instance.status].label} tone={PROCESS_STATUS_LABELS[instance.status].tone} />
        {instance.currentStepCode && (
          <Typography sx={{ color: colors.text.secondary }}>Шаг: {instance.currentStepCode}</Typography>
        )}
        {instance.parentInstanceId && (
          <Button
            size="small"
            onClick={() => navigate(`/processes/${instance.parentInstanceId}`)}
            sx={{ color: colors.text.secondary }}
          >
            Родительский процесс
          </Button>
        )}
        <RoleGate allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
          {canSuspend && (
            <Button size="small" variant="outlined" onClick={() => setSuspendOpen(true)}>
              Приостановить
            </Button>
          )}
          {canResume && (
            <Button
              size="small"
              variant="outlined"
              onClick={async () => {
                setActionError(null);
                try {
                  await resumeProcessInstance(instance.id);
                  load();
                } catch (err) {
                  setActionError(describeActionError(err, 'Не удалось возобновить процесс.'));
                }
              }}
            >
              Возобновить
            </Button>
          )}
          {canCancel && (
            <Button
              size="small"
              variant="outlined"
              onClick={() => {
                setSubOpen(true);
                listPublishedProcessDefinitions().then(setSubDefinitions).catch(() => setSubDefinitions([]));
              }}
            >
              Запустить подпроцесс
            </Button>
          )}
          {canCancel && (
            <Button size="small" color="error" variant="outlined" onClick={() => setCancelOpen(true)}>
              Отменить процесс
            </Button>
          )}
        </RoleGate>
      </Box>

      {actionError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {actionError}
        </Alert>
      )}

      {instance.status !== 'CANCELLED' ? (
        <Stepper activeStep={stepIndex(instance.status)} sx={{ mb: 4 }}>
          {STEPS.map((s) => (
            <Step key={s}>
              <StepLabel>{PROCESS_STATUS_LABELS[s].label}</StepLabel>
            </Step>
          ))}
        </Stepper>
      ) : (
        <Typography sx={{ mb: 4, color: '#F87171' }}>Этот процесс был отменён.</Typography>
      )}

      <ProcessMap rules={rules} currentStepCode={instance.currentStepCode} />

      <Typography variant="h6" gutterBottom sx={{ color: colors.text.heading }}>
        История событий
      </Typography>
      <Paper variant="outlined">
        <List dense>
          {events.map((event, idx) => (
            <Box key={event.id}>
              <ListItem>
                <ListItemText
                  primary={
                    <Typography sx={{ color: colors.text.body }}>
                      {event.eventType}
                      {event.actorId ? ` — ${event.actorId}` : ''}
                    </Typography>
                  }
                  secondary={
                    <Typography variant="caption" sx={{ color: colors.text.caption }}>
                      {new Date(event.occurredAt).toLocaleString('ru-RU')}
                    </Typography>
                  }
                />
              </ListItem>
              {idx < events.length - 1 && <Divider component="li" sx={{ borderColor: colors.glass.panelStroke }} />}
            </Box>
          ))}
          {events.length === 0 && (
            <ListItem>
              <ListItemText primary={<Typography sx={{ color: colors.text.secondary }}>Событий пока нет.</Typography>} />
            </ListItem>
          )}
        </List>
      </Paper>

      <Dialog open={suspendOpen} onClose={() => setSuspendOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Приостановить процесс</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ color: colors.text.secondary, mb: 1 }}>
            На время паузы срок SLA по задачам не течёт и сдвигается при возобновлении.
          </Typography>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label="Причина"
            value={suspendReason}
            onChange={(e) => setSuspendReason(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSuspendOpen(false)}>Отмена</Button>
          <Button
            variant="contained"
            disabled={!suspendReason.trim()}
            onClick={async () => {
              setActionError(null);
              try {
                await suspendProcessInstance(instance.id, suspendReason);
                setSuspendOpen(false);
                setSuspendReason('');
                load();
              } catch (err) {
                setActionError(describeActionError(err, 'Не удалось приостановить процесс.'));
              }
            }}
          >
            Приостановить
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={subOpen} onClose={() => setSubOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Запустить подпроцесс</DialogTitle>
        <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, pt: 3 }}>
          <TextField
            select
            label="Шаблон подпроцесса"
            value={subDefinitionId}
            onChange={(e) => setSubDefinitionId(e.target.value)}
          >
            {subDefinitions.map((definition) => (
              <MenuItem key={definition.id} value={definition.id}>
                {definition.code} v{definition.version}
              </MenuItem>
            ))}
            {subDefinitions.length === 0 && (
              <MenuItem disabled value="">
                Нет опубликованных шаблонов
              </MenuItem>
            )}
          </TextField>
          <TextField
            label="Бизнес-ключ"
            value={subBusinessKey}
            onChange={(e) => setSubBusinessKey(e.target.value)}
            helperText="Собственный идентификатор подпроцесса, например номер родительской заявки с суффиксом -SUB"
          />
          <TextField
            label="Атрибуты (JSON)"
            multiline
            minRows={3}
            value={subAttributes}
            onChange={(e) => setSubAttributes(e.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSubOpen(false)}>Закрыть</Button>
          <Button
            variant="contained"
            disabled={!subDefinitionId || !subBusinessKey.trim()}
            onClick={async () => {
              setActionError(null);
              let attributes: Record<string, unknown>;
              try {
                attributes = JSON.parse(subAttributes);
              } catch {
                setActionError('Атрибуты должны быть корректным JSON.');
                return;
              }
              try {
                const created = await startSubProcess(instance.id, {
                  processDefinitionId: subDefinitionId,
                  businessKey: subBusinessKey,
                  attributes,
                });
                setSubOpen(false);
                setSubBusinessKey('');
                navigate(`/processes/${created.id}`);
              } catch (err) {
                setActionError(describeActionError(err, 'Не удалось запустить подпроцесс.'));
              }
            }}
          >
            Запустить
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={cancelOpen} onClose={() => setCancelOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>Отменить процесс</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label="Причина"
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelOpen(false)}>Закрыть</Button>
          <Button
            color="error"
            disabled={!cancelReason.trim()}
            onClick={async () => {
              if (!id) return;
              setActionError(null);
              try {
                await cancelProcessInstance(id, cancelReason);
                setCancelOpen(false);
                setCancelReason('');
                load();
              } catch (err) {
                setActionError(describeActionError(err, 'Не удалось отменить процесс.'));
              }
            }}
          >
            Подтвердить отмену
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
