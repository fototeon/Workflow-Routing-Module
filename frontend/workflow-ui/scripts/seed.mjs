#!/usr/bin/env node
/**
 * Seeds a demo process template so the app has something to walk through right after
 * `docker compose up` — creates an SLA policy, a process definition with one routing rule, and
 * publishes it. Safe to re-run: uses a fixed demo code and skips creation if it already exists.
 */

const KEYCLOAK_URL = process.env.KEYCLOAK_URL ?? 'http://localhost:8081';
const REALM = process.env.KEYCLOAK_REALM ?? 'workflow';
const CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? 'workflow-ui';
const API_URL = process.env.API_URL ?? 'http://localhost:8090/api';
const ADMIN_USER = process.env.SEED_ADMIN_USER ?? 'admin1';
const ADMIN_PASSWORD = process.env.SEED_ADMIN_PASSWORD ?? 'admin123';

const DEMO_CODE = 'DEMO_EXPERTISE_REVIEW';

async function getAccessToken() {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: CLIENT_ID,
    username: ADMIN_USER,
    password: ADMIN_PASSWORD,
  });
  const response = await fetch(`${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  });
  if (!response.ok) {
    throw new Error(`Failed to obtain token: ${response.status} ${await response.text()}`);
  }
  const data = await response.json();
  return data.access_token;
}

async function api(token, method, path, body) {
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

async function main() {
  console.log('Fetching admin token...');
  const token = await getAccessToken();

  console.log('Checking for existing demo definitions...');
  const existing = await api(token, 'GET', `/process-definitions?code=${DEMO_CODE}`);
  if (existing.length > 0) {
    console.log(`Demo process "${DEMO_CODE}" already exists (${existing.length} version(s)). Skipping seed.`);
    return;
  }

  console.log('Creating SLA policy...');
  const slaPolicy = await api(token, 'POST', '/sla-policies', {
    code: 'DEMO_SLA',
    name: 'Demo 8-hour SLA',
    durationMinutes: 480,
    businessHoursOnly: true,
    escalationRules: [{ afterPercent: 80, escalateToRole: 'MANAGER' }],
  });
  console.log(`  -> ${slaPolicy.id}`);

  console.log('Creating process definition...');
  const definition = await api(token, 'POST', '/process-definitions', {
    code: DEMO_CODE,
    name: 'Demo expertise review',
    slaPolicyId: slaPolicy.id,
  });
  console.log(`  -> ${definition.id}`);

  console.log('Adding routing rule...');
  await api(token, 'POST', `/process-definitions/${definition.id}/routing-rules`, {
    name: 'route-complex-to-expert',
    priority: 0,
    conditionTree: { type: 'condition', field: 'requestType', op: 'EQ', value: 'COMPLEX' },
    targetStepCode: 'EXPERT_REVIEW',
    targetRole: 'COORDINATOR',
  });

  console.log('Publishing process definition...');
  await api(token, 'POST', `/process-definitions/${definition.id}/publish`);

  console.log('Seed complete. Demo process code:', DEMO_CODE);
}

main().catch((err) => {
  console.error(err);
  process.exitCode = 1;
});
