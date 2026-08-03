#!/usr/bin/env node
/**
 * Seeds the demo dataset so the app has something to walk through right after `docker compose up`:
 * SLA policies, process templates with routing rules, and a set of requests (process instances)
 * covering the module's features and every role. TEST-DATA.md in the repository root explains what
 * each seeded item is meant to exercise.
 *
 * Safe to re-run: every item is keyed by a fixed code / business key and skipped when it exists.
 */

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8081';
const REALM = process.env.KEYCLOAK_REALM ?? 'workflow';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'workflow-ui';
const API_URL = process.env.API_URL ?? 'http://localhost:8090/api';

/** Demo users from infra/keycloak/realm-export.json — actions run as the role that owns them. */
const USERS = {
  admin1: process.env.SEED_ADMIN_PASSWORD ?? 'admin123',
  manager1: process.env.SEED_MANAGER_PASSWORD ?? 'manager123',
  coordinator1: process.env.SEED_COORDINATOR_PASSWORD ?? 'coordinator123',
};

/** Matches nothing that is ever present in the attribute map, so it acts as a catch-all rule. */
const ALWAYS = { type: 'condition', field: '__fallback__', op: 'EXISTS', value: false };

const SLA_POLICIES = [
  {
    code: 'DEMO_SLA',
    name: 'Demo 8-hour SLA',
    durationMinutes: 480,
    businessHoursOnly: true,
    escalationRules: [{ afterPercent: 80, escalateToRole: 'MANAGER' }],
  },
  {
    // Short window on a round-the-clock calendar so escalation and breach are observable live.
    code: 'DEMO_SLA_FAST',
    name: 'Demo 5-minute SLA (escalates fast)',
    durationMinutes: 5,
    businessHoursOnly: false,
    escalationRules: [
      { afterPercent: 40, escalateToRole: 'MANAGER' },
      { afterPercent: 80, escalateToRole: 'ADMIN' },
    ],
  },
  {
    code: 'DEMO_SLA_24H',
    name: 'Demo 24-hour SLA (no escalation)',
    durationMinutes: 1440,
    businessHoursOnly: false,
    escalationRules: [],
  },
];

const DEFINITIONS = [
  {
    code: 'DEMO_EXPERTISE_REVIEW',
    name: 'Demo expertise review',
    slaPolicyCode: 'DEMO_SLA',
    publish: true,
    rules: [
      {
        name: 'route-complex-to-expert',
        priority: 0,
        conditionTree: { type: 'condition', field: 'requestType', op: 'EQ', value: 'COMPLEX' },
        targetStepCode: 'EXPERT_REVIEW',
        targetRole: 'COORDINATOR',
      },
    ],
  },
  {
    code: 'DEMO_MULTI_ROUTE',
    name: 'Demo multi-branch routing',
    slaPolicyCode: 'DEMO_SLA',
    publish: true,
    rules: [
      {
        // AND group over GTE + IN — the highest-priority branch.
        name: 'large-amount-in-capital-regions',
        priority: 10,
        conditionTree: {
          type: 'group',
          op: 'AND',
          children: [
            { type: 'condition', field: 'amount', op: 'GTE', value: 1000000 },
            { type: 'condition', field: 'region', op: 'IN', value: ['MSK', 'SPB'] },
          ],
        },
        targetStepCode: 'HEAD_REVIEW',
        targetRole: 'MANAGER',
      },
      {
        name: 'simple-requests-to-analyst',
        priority: 20,
        conditionTree: { type: 'condition', field: 'requestType', op: 'EQ', value: 'SIMPLE' },
        targetStepCode: 'AUTO_CHECK',
        targetRole: 'ANALYST',
      },
      {
        name: 'fallback-standard-review',
        priority: 30,
        conditionTree: ALWAYS,
        targetStepCode: 'STANDARD_REVIEW',
        targetRole: 'COORDINATOR',
      },
    ],
  },
  {
    code: 'DEMO_URGENT',
    name: 'Demo urgent review (5-minute SLA)',
    slaPolicyCode: 'DEMO_SLA_FAST',
    publish: true,
    rules: [
      {
        name: 'high-priority-to-urgent-review',
        priority: 0,
        conditionTree: { type: 'condition', field: 'priority', op: 'EQ', value: 'HIGH' },
        targetStepCode: 'URGENT_REVIEW',
        targetRole: 'COORDINATOR',
      },
    ],
  },
  {
    code: 'DEMO_SUBPROCESS_CHECK',
    name: 'Demo document check (sub-process)',
    slaPolicyCode: 'DEMO_SLA_24H',
    publish: true,
    rules: [
      {
        name: 'always-to-document-check',
        priority: 0,
        conditionTree: ALWAYS,
        targetStepCode: 'DOCUMENT_CHECK',
        targetRole: 'ANALYST',
      },
    ],
  },
  {
    // Left as a draft on purpose: publishing it must fail until a routing rule is added.
    code: 'DEMO_DRAFT_NO_RULES',
    name: 'Demo draft without routing rules',
    slaPolicyCode: null,
    publish: false,
    rules: [],
  },
];

const REQUESTS = [
  {
    businessKey: 'REQ-2026-001',
    definitionCode: 'DEMO_EXPERTISE_REVIEW',
    attributes: { requestType: 'COMPLEX', applicant: 'ООО «Ромашка»' },
    startedBy: 'coordinator1',
  },
  {
    businessKey: 'REQ-2026-002',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'COMPLEX', amount: 2500000, region: 'MSK', organizationId: 'ORG-42' },
    startedBy: 'coordinator1',
    subProcess: { definitionCode: 'DEMO_SUBPROCESS_CHECK', businessKey: 'REQ-2026-002-SUB', startedBy: 'coordinator1' },
  },
  {
    businessKey: 'REQ-2026-003',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'SIMPLE', amount: 5000, region: 'MSK' },
    startedBy: 'coordinator1',
  },
  {
    businessKey: 'REQ-2026-004',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'COMPLEX', amount: 900000, region: 'NSK' },
    startedBy: 'manager1',
  },
  {
    businessKey: 'REQ-2026-005',
    definitionCode: 'DEMO_EXPERTISE_REVIEW',
    attributes: { requestType: 'COMPLEX', applicant: 'АО «Вектор»' },
    startedBy: 'coordinator1',
    then: 'complete',
  },
  {
    businessKey: 'REQ-2026-006',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'COMPLEX', amount: 3000000, region: 'SPB' },
    startedBy: 'coordinator1',
    then: 'reassign',
  },
  {
    businessKey: 'REQ-2026-007',
    definitionCode: 'DEMO_EXPERTISE_REVIEW',
    attributes: { requestType: 'COMPLEX', applicant: 'ИП Петров' },
    startedBy: 'coordinator1',
    then: 'cancel',
  },
  {
    businessKey: 'REQ-2026-009',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'COMPLEX', amount: 1500000, region: 'SPB', organizationId: 'ORG-77' },
    startedBy: 'coordinator1',
    then: 'suspend',
  },
  {
    businessKey: 'REQ-2026-008',
    definitionCode: 'DEMO_URGENT',
    attributes: { priority: 'HIGH', applicant: 'ООО «Скорость»' },
    startedBy: 'coordinator1',
  },
];

const tokens = new Map();

async function tokenFor(username) {
  if (tokens.has(username)) {
    return tokens.get(username);
  }
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: CLIENT_ID,
    username,
    password: USERS[username],
  });
  const response = await fetch(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!response.ok) {
    throw new Error(`Failed to obtain token for ${username}: ${response.status} ${await response.text()}`);
  }
  const token = (await response.json()).access_token;
  tokens.set(username, token);
  return token;
}

async function api(username, method, path, body) {
  const token = await tokenFor(username);
  const response = await fetch(`${API_URL}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`${method} ${path} failed: ${response.status} ${await response.text()}`);
  }
  return response.status === 204 ? null : response.json();
}

async function ensureSlaPolicies() {
  const existing = await api('admin1', 'GET', '/sla-policies');
  const byCode = new Map(existing.map((policy) => [policy.code, policy]));

  for (const policy of SLA_POLICIES) {
    if (byCode.has(policy.code)) {
      console.log(`  = SLA ${policy.code} (exists)`);
      continue;
    }
    const created = await api('admin1', 'POST', '/sla-policies', policy);
    byCode.set(policy.code, created);
    console.log(`  + SLA ${policy.code}`);
  }
  return byCode;
}

async function ensureDefinitions(slaByCode) {
  const byCode = new Map();

  for (const definition of DEFINITIONS) {
    const existing = await api('admin1', 'GET', `/process-definitions?code=${definition.code}`);
    if (existing.length > 0) {
      byCode.set(definition.code, existing[0]);
      console.log(`  = template ${definition.code} (exists)`);
      continue;
    }

    const created = await api('admin1', 'POST', '/process-definitions', {
      code: definition.code,
      name: definition.name,
      slaPolicyId: definition.slaPolicyCode ? slaByCode.get(definition.slaPolicyCode).id : null,
    });
    for (const rule of definition.rules) {
      await api('admin1', 'POST', `/process-definitions/${created.id}/routing-rules`, rule);
    }
    if (definition.publish) {
      await api('admin1', 'POST', `/process-definitions/${created.id}/publish`);
    }
    byCode.set(definition.code, created);
    console.log(`  + template ${definition.code} (${definition.rules.length} rule(s), ${definition.publish ? 'published' : 'draft'})`);
  }
  return byCode;
}

async function firstTaskOf(instanceId) {
  const page = await api('admin1', 'GET', `/tasks?processInstanceId=${instanceId}&size=1`);
  if (page.content.length === 0) {
    throw new Error(`No task found for process instance ${instanceId}`);
  }
  return page.content[0];
}

async function instanceExists(businessKey) {
  const page = await api('admin1', 'GET', `/process-instances?businessKey=${encodeURIComponent(businessKey)}&size=1`);
  return page.totalElements > 0;
}

async function ensureRequests(definitionsByCode) {
  for (const request of REQUESTS) {
    if (await instanceExists(request.businessKey)) {
      console.log(`  = request ${request.businessKey} (exists)`);
      continue;
    }

    const instance = await api(request.startedBy, 'POST', '/process-instances', {
      processDefinitionId: definitionsByCode.get(request.definitionCode).id,
      businessKey: request.businessKey,
      attributes: request.attributes,
      correlationId: `seed-${request.businessKey}`,
    });

    let note = `step ${instance.currentStepCode}`;

    if (request.then === 'complete') {
      const task = await firstTaskOf(instance.id);
      await api('coordinator1', 'POST', `/tasks/${task.id}/complete`, {});
      note = 'completed';
    } else if (request.then === 'reassign') {
      const task = await firstTaskOf(instance.id);
      await api('manager1', 'POST', `/tasks/${task.id}/reassign`, {
        toAssignee: 'analyst1',
        reason: 'Перераспределение нагрузки: основной эксперт занят',
      });
      note = `${instance.currentStepCode}, reassigned to analyst1`;
    } else if (request.then === 'suspend') {
      await api('manager1', 'POST', `/process-instances/${instance.id}/suspend`, {
        reason: 'Ожидание документов от заявителя',
      });
      note = `${instance.currentStepCode}, suspended`;
    } else if (request.then === 'cancel') {
      await api('manager1', 'POST', `/process-instances/${instance.id}/cancel`, {
        reason: 'Заявитель отозвал заявку',
      });
      note = 'cancelled';
    }

    if (request.subProcess) {
      await api(request.subProcess.startedBy, 'POST', `/process-instances/${instance.id}/sub-processes`, {
        processDefinitionId: definitionsByCode.get(request.subProcess.definitionCode).id,
        businessKey: request.subProcess.businessKey,
        attributes: {},
        correlationId: `seed-${request.subProcess.businessKey}`,
      });
      note += `, sub-process ${request.subProcess.businessKey}`;
    }

    console.log(`  + request ${request.businessKey} (${note})`);
  }
}

async function main() {
  console.log('SLA policies:');
  const slaByCode = await ensureSlaPolicies();

  console.log('Process templates:');
  const definitionsByCode = await ensureDefinitions(slaByCode);

  console.log('Requests (process instances):');
  await ensureRequests(definitionsByCode);

  console.log('\nSeed complete. See TEST-DATA.md for what each item is meant to exercise.');
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
