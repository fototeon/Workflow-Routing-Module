export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface SlaPolicy {
  id: string;
  code: string;
  name: string;
  durationMinutes: number;
  businessHoursOnly: boolean;
  escalationRules: EscalationRule[];
}

export interface EscalationRule {
  afterPercent: number;
  escalateToRole: string;
}

export type ProcessDefinitionStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface ProcessDefinition {
  id: string;
  code: string;
  name: string;
  version: number;
  status: ProcessDefinitionStatus;
  slaPolicyId: string | null;
  /** A draft without routing rules cannot be published — the catalogue shows this to explain why. */
  routingRuleCount: number;
  createdAt: string;
  updatedAt: string;
}

export type ConditionNode = GroupNode | LeafCondition;

export interface GroupNode {
  type: 'group';
  op: 'AND' | 'OR' | 'NOT';
  children: ConditionNode[];
}

export interface LeafCondition {
  type: 'condition';
  field: string;
  op: 'EQ' | 'NEQ' | 'GT' | 'GTE' | 'LT' | 'LTE' | 'IN' | 'NOT_IN' | 'CONTAINS' | 'EXISTS';
  value: unknown;
}

export interface RoutingRule {
  id: string;
  processDefinitionId: string;
  name: string;
  priority: number;
  conditionTree: ConditionNode;
  targetStepCode: string;
  targetRole: string | null;
}

export type ProcessInstanceStatus =
  | 'NOT_STARTED'
  | 'RUNNING'
  | 'WAITING_EXTERNAL'
  | 'SUSPENDED'
  | 'OVERDUE'
  | 'COMPLETED'
  | 'CANCELLED';

export interface ProcessInstance {
  id: string;
  processDefinitionId: string;
  processVersion: number;
  businessKey: string;
  status: ProcessInstanceStatus;
  currentStepCode: string | null;
  parentInstanceId: string | null;
  startedAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export type TaskInstanceStatus = 'CREATED' | 'IN_PROGRESS' | 'WAITING' | 'OVERDUE' | 'COMPLETED' | 'CANCELLED';

export interface TaskInstance {
  id: string;
  processInstanceId: string;
  stepCode: string;
  name: string;
  assigneeId: string | null;
  assigneeRole: string | null;
  status: TaskInstanceStatus;
  dueAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ProcessEventLogEntry {
  id: string;
  processInstanceId: string | null;
  taskInstanceId: string | null;
  eventType: string;
  payload: Record<string, unknown>;
  correlationId: string | null;
  actorId: string | null;
  occurredAt: string;
}
