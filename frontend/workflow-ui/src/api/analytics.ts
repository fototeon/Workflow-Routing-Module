import { apiClient } from './client';

export interface AnalyticsSummary {
  totalProcesses: number;
  totalTasks: number;
  processesByStatus: Record<string, number>;
  tasksByStatus: Record<string, number>;
  processesByTemplate: Record<string, number>;
  overdueTasks: number;
  slaBreachedTasks: number;
  slaMetTasks: number;
  averageCompletionMinutes: number | null;
}

export async function getAnalyticsSummary(): Promise<AnalyticsSummary> {
  const { data } = await apiClient.get<AnalyticsSummary>('/analytics/summary');
  return data;
}

/** Downloads a CSV export through the API client, so the request carries the access token. */
export async function downloadCsv(path: string, params: Record<string, unknown>, filename: string): Promise<void> {
  const { data } = await apiClient.get<Blob>(path, { params, responseType: 'blob' });
  const url = URL.createObjectURL(data);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
