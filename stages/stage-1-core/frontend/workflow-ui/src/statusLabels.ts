import type { StatusTone } from './components/StatusChip';
import type { ProcessDefinitionStatus, ProcessInstanceStatus, TaskInstanceStatus } from './api/types';

export const DEFINITION_STATUS_LABELS: Record<ProcessDefinitionStatus, { label: string; tone: StatusTone }> = {
  DRAFT: { label: 'Черновик', tone: 'neutral' },
  PUBLISHED: { label: 'Опубликован', tone: 'success' },
  ARCHIVED: { label: 'В архиве', tone: 'neutral' },
};

export const PROCESS_STATUS_LABELS: Record<ProcessInstanceStatus, { label: string; tone: StatusTone }> = {
  NOT_STARTED: { label: 'Не запущен', tone: 'neutral' },
  RUNNING: { label: 'Выполняется', tone: 'info' },
  COMPLETED: { label: 'Завершён', tone: 'success' },
  CANCELLED: { label: 'Отменён', tone: 'neutral' },
};

export const TASK_STATUS_LABELS: Record<TaskInstanceStatus, { label: string; tone: StatusTone }> = {
  CREATED: { label: 'Создана', tone: 'neutral' },
  IN_PROGRESS: { label: 'В работе', tone: 'info' },
  COMPLETED: { label: 'Выполнено', tone: 'success' },
  CANCELLED: { label: 'Отменена', tone: 'neutral' },
};
