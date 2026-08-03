import { useEffect, useState } from 'react';
import { Alert, Box, Button, LinearProgress, Paper, Stack, Typography } from '@mui/material';
import DownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined';
import { downloadCsv, getAnalyticsSummary, type AnalyticsSummary } from '../api/analytics';
import { describeLoadError } from '../api/errors';
import { PROCESS_STATUS_LABELS, TASK_STATUS_LABELS } from '../statusLabels';
import type { ProcessInstanceStatus, TaskInstanceStatus } from '../api/types';
import { colors } from '../colors';

/** Reporting surface of the analyst role: indicators plus the exports (TZ §3, §9). */
export function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    getAnalyticsSummary()
      .then((result) => {
        if (!cancelled) setSummary(result);
      })
      .catch((error) => {
        if (!cancelled) setLoadError(describeLoadError(error, 'показатели'));
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (loadError) {
    return <Alert severity="error">{loadError}</Alert>;
  }
  if (!summary) {
    return <Typography sx={{ color: colors.text.secondary }}>Загрузка…</Typography>;
  }

  const slaTotal = summary.slaBreachedTasks + summary.slaMetTasks;
  const slaShare = slaTotal === 0 ? null : Math.round((summary.slaMetTasks / slaTotal) * 100);

  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ justifyContent: 'flex-end', mb: 2 }}>
        <Button
          startIcon={<DownloadOutlinedIcon />}
          onClick={() => downloadCsv('/process-instances/export', {}, 'process-instances.csv')}
        >
          Выгрузить процессы (CSV)
        </Button>
        <Button startIcon={<DownloadOutlinedIcon />} onClick={() => downloadCsv('/tasks/export', {}, 'tasks.csv')}>
          Выгрузить задачи (CSV)
        </Button>
      </Stack>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 2, mb: 3 }}>
        <Metric label="Всего процессов" value={summary.totalProcesses} />
        <Metric label="Всего задач" value={summary.totalTasks} />
        <Metric label="Просроченные задачи" value={summary.overdueTasks} accent={summary.overdueTasks > 0} />
        <Metric
          label="Соблюдение SLA"
          value={slaShare === null ? '—' : `${slaShare}%`}
          hint={slaTotal === 0 ? 'нет завершённых задач со сроком' : `${summary.slaMetTasks} из ${slaTotal} в срок`}
        />
        <Metric
          label="Среднее время процесса"
          value={summary.averageCompletionMinutes === null ? '—' : formatMinutes(summary.averageCompletionMinutes)}
          hint="по завершённым"
        />
      </Box>

      <Box sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: 2 }}>
        <Distribution
          title="Процессы по статусам"
          data={summary.processesByStatus}
          total={summary.totalProcesses}
          labelOf={(key) => PROCESS_STATUS_LABELS[key as ProcessInstanceStatus]?.label ?? key}
        />
        <Distribution
          title="Задачи по статусам"
          data={summary.tasksByStatus}
          total={summary.totalTasks}
          labelOf={(key) => TASK_STATUS_LABELS[key as TaskInstanceStatus]?.label ?? key}
        />
        <Distribution
          title="Процессы по шаблонам"
          data={summary.processesByTemplate}
          total={summary.totalProcesses}
          labelOf={(key) => key}
        />
      </Box>
    </Box>
  );
}

function formatMinutes(minutes: number): string {
  if (minutes < 60) return `${minutes} мин`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours < 24) return rest === 0 ? `${hours} ч` : `${hours} ч ${rest} мин`;
  const days = Math.floor(hours / 24);
  return `${days} дн ${hours % 24} ч`;
}

function Metric({ label, value, hint, accent }: { label: string; value: number | string; hint?: string; accent?: boolean }) {
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="body2" sx={{ color: colors.text.secondary, mb: 0.5 }}>
        {label}
      </Typography>
      <Typography variant="h4" sx={{ color: accent ? colors.status.warning.text : colors.text.heading, fontWeight: 700 }}>
        {value}
      </Typography>
      {hint && (
        <Typography variant="caption" sx={{ color: colors.text.caption }}>
          {hint}
        </Typography>
      )}
    </Paper>
  );
}

function Distribution({
  title,
  data,
  total,
  labelOf,
}: {
  title: string;
  data: Record<string, number>;
  total: number;
  labelOf: (key: string) => string;
}) {
  const entries = Object.entries(data);
  return (
    <Paper sx={{ p: 2.5 }}>
      <Typography variant="subtitle1" sx={{ color: colors.text.heading, mb: 2 }}>
        {title}
      </Typography>
      {entries.length === 0 && (
        <Typography variant="body2" sx={{ color: colors.text.secondary }}>
          Нет данных.
        </Typography>
      )}
      <Stack spacing={1.5}>
        {entries.map(([key, count]) => (
          <Box key={key}>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
              <Typography variant="body2" sx={{ color: colors.text.body }}>
                {labelOf(key)}
              </Typography>
              <Typography variant="body2" sx={{ color: colors.text.secondary }}>
                {count}
              </Typography>
            </Box>
            <LinearProgress
              variant="determinate"
              value={total === 0 ? 0 : Math.round((count / total) * 100)}
              sx={{ height: 6, borderRadius: 3 }}
            />
          </Box>
        ))}
      </Stack>
    </Paper>
  );
}
