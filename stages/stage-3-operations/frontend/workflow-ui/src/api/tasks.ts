import { apiClient } from './client';
import type { PageResponse, TaskInstance, TaskInstanceStatus } from './types';

export interface TaskFilter {
  status?: TaskInstanceStatus;
  assigneeId?: string;
  assigneeRole?: string;
  processInstanceId?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function searchTasks(filter: TaskFilter): Promise<PageResponse<TaskInstance>> {
  const { data } = await apiClient.get<PageResponse<TaskInstance>>('/tasks', { params: filter });
  return data;
}

export async function getTask(id: string): Promise<TaskInstance> {
  const { data } = await apiClient.get<TaskInstance>(`/tasks/${id}`);
  return data;
}

export async function completeTask(id: string, outcomeAttributes?: Record<string, unknown>, correlationId?: string): Promise<TaskInstance> {
  const { data } = await apiClient.post<TaskInstance>(`/tasks/${id}/complete`, { outcomeAttributes, correlationId });
  return data;
}

export interface ReassignTarget {
  /** Exactly one of the two: a specific person takes the task, or it goes back to a role queue. */
  toAssignee?: string;
  toRole?: string;
}

export async function reassignTask(id: string, target: ReassignTarget, reason: string): Promise<TaskInstance> {
  const { data } = await apiClient.post<TaskInstance>(`/tasks/${id}/reassign`, { ...target, reason });
  return data;
}
