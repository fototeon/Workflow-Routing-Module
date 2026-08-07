import { apiClient } from './client';
import type { PageResponse, ProcessInstance, ProcessInstanceStatus } from './types';

export interface StartProcessInput {
  processDefinitionId: string;
  businessKey: string;
  attributes?: Record<string, unknown>;
}

export interface ProcessInstanceFilter {
  status?: ProcessInstanceStatus;
  businessKey?: string;
  processDefinitionCode?: string;
  page?: number;
  size?: number;
}

export async function searchProcessInstances(filter: ProcessInstanceFilter): Promise<PageResponse<ProcessInstance>> {
  const { data } = await apiClient.get<PageResponse<ProcessInstance>>('/process-instances', { params: filter });
  return data;
}

export async function startProcessInstance(input: StartProcessInput): Promise<ProcessInstance> {
  const { data } = await apiClient.post<ProcessInstance>('/process-instances', input);
  return data;
}

export async function cancelProcessInstance(id: string): Promise<ProcessInstance> {
  const { data } = await apiClient.post<ProcessInstance>(`/process-instances/${id}/cancel`);
  return data;
}
