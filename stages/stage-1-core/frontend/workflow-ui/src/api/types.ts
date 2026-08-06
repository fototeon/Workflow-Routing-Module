export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type ProcessDefinitionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface ProcessDefinition {
  id: string;
  code: string;
  name: string;
  version: number;
  status: ProcessDefinitionStatus;
  createdAt: string;
  updatedAt: string;
}

/** One comparison against the case attributes; a routing rule carries exactly one of them. */
export interface LeafCondition {
  type: 'condition';
  field: string;
  op: 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE';
  value: unknown;
}

export interface RoutingRule {
  id: string;
  processDefinitionId: string;
  name: string;
  priority: number;
  conditionTree: LeafCondition;
  targetStepCode: string;
  targetRole: string | null;
}

export type ProcessInstanceStatus = 'NOT_STARTED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED';

export interface ProcessInstance {
  id: string;
  processDefinitionId: string;
  processVersion: number;
  businessKey: string;
  status: ProcessInstanceStatus;
  currentStepCode: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type TaskInstanceStatus = 'CREATED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface TaskInstance {
  id: string;
  processInstanceId: string;
  stepCode: string;
  name: string;
  assigneeId: string | null;
  assigneeRole: string | null;
  status: TaskInstanceStatus;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}
