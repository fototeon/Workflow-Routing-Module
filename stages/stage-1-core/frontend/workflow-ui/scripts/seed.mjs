#!/usr/bin/env node
/**
 * Seeds a demo dataset so the app has something to walk through right after `docker compose up`:
 * process templates with routing rules and a few requests (process instances) in different states.
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

const DEFINITIONS = [
  {
    code: 'DEMO_EXPERTISE_REVIEW',
    name: 'Demo expertise review',
    publish: true,
    rules: [
      {
        name: 'route-complex-to-expert',
        priority: 10,
        conditionTree: { type: 'condition', field: 'requestType', op: 'EQ', value: 'COMPLEX' },
        targetStepCode: 'EXPERT_REVIEW',
        targetRole: 'COORDINATOR',
      },
    ],
  },
  {
    code: 'DEMO_MULTI_ROUTE',
    name: 'Demo multi-branch routing',
    publish: true,
    rules: [
      {
        // Checked first: a large amount goes straight to the head of department.
        name: 'large-amount',
        priority: 10,
        conditionTree: { type: 'condition', field: 'amount', op: 'GTE', value: 1000000 },
        targetStepCode: 'HEAD_REVIEW',
        targetRole: 'MANAGER',
      },
      {
        name: 'simple-request',
        priority: 20,
        conditionTree: { type: 'condition', field: 'requestType', op: 'EQ', value: 'SIMPLE' },
        targetStepCode: 'AUTO_CHECK',
        targetRole: 'ANALYST',
      },
    ],
  },
  {
    // Deliberately left as a draft with no rules: publishing it must be refused.
    code: 'DEMO_DRAFT_NO_RULES',
    name: 'Demo draft without routing rules',
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
    attributes: { requestType: 'COMPLEX', amount: 2500000 },
    startedBy: 'coordinator1',
  },
  {
    businessKey: 'REQ-2026-003',
    definitionCode: 'DEMO_MULTI_ROUTE',
    attributes: { requestType: 'SIMPLE', amount: 5000 },
    startedBy: 'coordinator1',
  },
  {
    businessKey: 'REQ-2026-004',
    definitionCode: 'DEMO_EXPERTISE_REVIEW',
    attributes: { requestType: 'COMPLEX', applicant: 'АО «Вектор»' },
    startedBy: 'coordinator1',
    then: 'complete',
  },
  {
    businessKey: 'REQ-2026-005',
    definitionCode: 'DEMO_EXPERTISE_REVIEW',
    attributes: { requestType: 'COMPLEX', applicant: 'ИП Петров' },
    startedBy: 'coordinator1',
    then: 'cancel',
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

async function ensureDefinitions() {
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
    });

    let note = `step ${instance.currentStepCode}`;

    if (request.then === 'complete') {
      const task = await firstTaskOf(instance.id);
      await api('coordinator1', 'POST', `/tasks/${task.id}/complete`, {});
      note = 'completed';
    } else if (request.then === 'cancel') {
      await api('manager1', 'POST', `/process-instances/${instance.id}/cancel`);
      note = 'cancelled';
    }

    console.log(`  + request ${request.businessKey} (${note})`);
  }
}

async function main() {
  console.log('Process templates:');
  const definitionsByCode = await ensureDefinitions();

  console.log('Requests (process instances):');
  await ensureRequests(definitionsByCode);

  console.log('\nSeed complete.');
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
