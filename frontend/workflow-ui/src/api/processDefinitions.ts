import { apiClient } from './client';
import type { ConditionNode, ProcessDefinition, ProcessEventLogEntry, RoutingRule } from './types';

export interface ProcessDefinitionInput {
  code: string;
  name: string;
  slaPolicyId?: string | null;
}

export interface RoutingRuleInput {
  name: string;
  priority: number;
  conditionTree: ConditionNode;
  targetStepCode: string;
  targetRole?: string | null;
}

export async function listProcessDefinitionVersions(code: string): Promise<ProcessDefinition[]> {
  const { data } = await apiClient.get<ProcessDefinition[]>('/process-definitions', { params: { code } });
  return data;
}

export async function listPublishedProcessDefinitions(): Promise<ProcessDefinition[]> {
  const { data } = await apiClient.get<ProcessDefinition[]>('/process-definitions');
  return data;
}

export async function getProcessDefinition(id: string): Promise<ProcessDefinition> {
  const { data } = await apiClient.get<ProcessDefinition>(`/process-definitions/${id}`);
  return data;
}

export async function createProcessDefinition(input: ProcessDefinitionInput): Promise<ProcessDefinition> {
  const { data } = await apiClient.post<ProcessDefinition>('/process-definitions', input);
  return data;
}

export async function updateProcessDefinition(id: string, input: ProcessDefinitionInput): Promise<ProcessDefinition> {
  const { data } = await apiClient.put<ProcessDefinition>(`/process-definitions/${id}`, input);
  return data;
}

export async function publishProcessDefinition(id: string): Promise<ProcessDefinition> {
  const { data } = await apiClient.post<ProcessDefinition>(`/process-definitions/${id}/publish`);
  return data;
}

export async function archiveProcessDefinition(id: string): Promise<ProcessDefinition> {
  const { data } = await apiClient.post<ProcessDefinition>(`/process-definitions/${id}/archive`);
  return data;
}

export async function getProcessDefinitionJournal(id: string): Promise<ProcessEventLogEntry[]> {
  const { data } = await apiClient.get<ProcessEventLogEntry[]>(`/process-definitions/${id}/journal`);
  return data;
}

export async function listRoutingRules(definitionId: string): Promise<RoutingRule[]> {
  const { data } = await apiClient.get<RoutingRule[]>(`/process-definitions/${definitionId}/routing-rules`);
  return data;
}

export async function addRoutingRule(definitionId: string, input: RoutingRuleInput): Promise<RoutingRule> {
  const { data } = await apiClient.post<RoutingRule>(`/process-definitions/${definitionId}/routing-rules`, input);
  return data;
}

export async function deleteRoutingRule(definitionId: string, ruleId: string): Promise<void> {
  await apiClient.delete(`/process-definitions/${definitionId}/routing-rules/${ruleId}`);
}
