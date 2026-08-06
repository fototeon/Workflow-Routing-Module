import { apiClient } from './client';
import type { SlaPolicy } from './types';

export interface SlaPolicyInput {
  code: string;
  name: string;
  durationMinutes: number;
  businessHoursOnly: boolean;
  escalationRules: SlaPolicy['escalationRules'];
}

export async function listSlaPolicies(): Promise<SlaPolicy[]> {
  const { data } = await apiClient.get<SlaPolicy[]>('/sla-policies');
  return data;
}

export async function getSlaPolicy(id: string): Promise<SlaPolicy> {
  const { data } = await apiClient.get<SlaPolicy>(`/sla-policies/${id}`);
  return data;
}

export async function createSlaPolicy(input: SlaPolicyInput): Promise<SlaPolicy> {
  const { data } = await apiClient.post<SlaPolicy>('/sla-policies', input);
  return data;
}

export async function updateSlaPolicy(id: string, input: SlaPolicyInput): Promise<SlaPolicy> {
  const { data } = await apiClient.put<SlaPolicy>(`/sla-policies/${id}`, input);
  return data;
}
