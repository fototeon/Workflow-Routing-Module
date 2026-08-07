import { apiClient } from './client';
import type { PageResponse, TaskInstance, TaskInstanceStatus } from './types';

export interface TaskFilter {
  status?: TaskInstanceStatus;
  assigneeId?: string;
  assigneeRole?: string;
  processInstanceId?: string;
  page?: number;
  size?: number;
}

export async function searchTasks(filter: TaskFilter): Promise<PageResponse<TaskInstance>> {
  const { data } = await apiClient.get<PageResponse<TaskInstance>>('/tasks', { params: filter });
  return data;
}

export async function completeTask(id: string, outcomeAttributes?: Record<string, unknown>): Promise<TaskInstance> {
  const { data } = await apiClient.post<TaskInstance>(`/tasks/${id}/complete`, { outcomeAttributes });
  return data;
}
