import { apiClient } from './client';
import type { PageResponse, ProcessEventLogEntry, ProcessInstance, ProcessInstanceStatus } from './types';

export interface StartProcessInput {
  processDefinitionId: string;
  businessKey: string;
  attributes?: Record<string, unknown>;
  correlationId?: string;
}

export interface ProcessInstanceFilter {
  status?: ProcessInstanceStatus;
  businessKey?: string;
  processDefinitionCode?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export async function searchProcessInstances(filter: ProcessInstanceFilter): Promise<PageResponse<ProcessInstance>> {
  const { data } = await apiClient.get<PageResponse<ProcessInstance>>('/process-instances', { params: filter });
  return data;
}

export async function getProcessInstance(id: string): Promise<ProcessInstance> {
  const { data } = await apiClient.get<ProcessInstance>(`/process-instances/${id}`);
  return data;
}

export async function startProcessInstance(input: StartProcessInput): Promise<ProcessInstance> {
  const { data } = await apiClient.post<ProcessInstance>('/process-instances', input);
  return data;
}

export async function cancelProcessInstance(id: string, reason: string): Promise<ProcessInstance> {
  const { data } = await apiClient.post<ProcessInstance>(`/process-instances/${id}/cancel`, { reason });
  return data;
}

export async function getProcessInstanceEvents(id: string): Promise<ProcessEventLogEntry[]> {
  const { data } = await apiClient.get<ProcessEventLogEntry[]>(`/process-instances/${id}/events`);
  return data;
}
