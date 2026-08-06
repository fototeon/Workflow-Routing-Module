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
import { cancelProcessInstance, getProcessInstance, getProcessInstanceEvents } from '../api/processInstances';
import { listRoutingRules } from '../api/processDefinitions';
import type { ProcessEventLogEntry, ProcessInstance, ProcessInstanceStatus, RoutingRule } from '../api/types';
import { ProcessMap } from '../components/ProcessMap';
import { RoleGate } from '../auth/RoleGate';
import { ROLES } from '../auth/authConfig';
import { StatusChip } from '../components/StatusChip';
import { PROCESS_STATUS_LABELS } from '../statusLabels';
import { describeActionError } from '../api/errors';
import { colors } from '../colors';

const STEPS: ProcessInstanceStatus[] = ['NOT_STARTED', 'RUNNING', 'COMPLETED'];

function stepIndex(status: ProcessInstanceStatus): number {
  if (status === 'CANCELLED') return -1;
  if (['OVERDUE', 'WAITING_EXTERNAL'].includes(status)) return 1;
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
  const [rules, setRules] = useState<RoutingRule[]>([]);
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
        <Button size="small" onClick={() => navigate(`/tasks?processInstanceId=${instance.id}`)}>
          Задачи заявки
        </Button>
        <RoleGate allow={[ROLES.COORDINATOR, ROLES.MANAGER, ROLES.ADMIN]}>
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
