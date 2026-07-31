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

export async function reassignTask(id: string, toAssignee: string, reason: string): Promise<TaskInstance> {
  const { data } = await apiClient.post<TaskInstance>(`/tasks/${id}/reassign`, { toAssignee, reason });
  return data;
}
