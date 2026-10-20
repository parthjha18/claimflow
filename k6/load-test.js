// ClaimFlow load test: the submit and approve endpoints.
//
//   k6 run k6/load-test.js                                   (k6 installed locally)
//   docker run --rm -i --network claimflow_default -v "$PWD/k6:/k6" \
//       -e BASE_URL=http://app:8080 grafana/k6 run /k6/load-test.js       (against `docker compose up`)
//
// setup() creates its own throw-away organisation (no demo data needed): 1 manager, N employees, and
// PRE_CLAIMS claims that are already SUBMITTED, waiting for the manager.
//   scenario "submit":  each iteration = create a draft + POST /submit (20% also replay the same
//                       Idempotency-Key, which must return the stored result)
//   scenario "approve": each iteration approves one pre-submitted claim; every 5th iteration approves it
//                       a second time, which the state machine must answer with 409
import http from 'k6/http';
import exec from 'k6/execution';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const EMPLOYEES = parseInt(__ENV.EMPLOYEES || '20');
const PRE_CLAIMS = parseInt(__ENV.PRE_CLAIMS || '300');
const VUS = parseInt(__ENV.VUS || '20');
const SUBMIT_DURATION = __ENV.SUBMIT_DURATION || '30s';

const replays = new Counter('idempotent_replays');
const doubleApproveRejected = new Counter('double_approve_rejected_409');

// 409 is an expected answer for the duplicate-approve probe, so it must not count as a failed request.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

export const options = {
  scenarios: {
    submit: {
      executor: 'constant-vus',
      vus: VUS,
      duration: SUBMIT_DURATION,
      exec: 'submitFlow',
      startTime: '0s',
    },
    approve: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: PRE_CLAIMS,
      maxDuration: '2m',
      exec: 'approveFlow',
      startTime: `${parseInt(SUBMIT_DURATION) + 5}s`,   // after the submit scenario, so results don't mix
    },
  },
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'http_req_duration{endpoint:submit}': ['p(95)<500'],
    'http_req_duration{endpoint:approve}': ['p(95)<500'],
    'checks': ['rate>0.99'],
  },
};

function json(token, body, extraHeaders) {
  const headers = Object.assign({ 'Content-Type': 'application/json' }, extraHeaders || {});
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers, body: body === undefined ? undefined : JSON.stringify(body) };
}

function post(path, token, body, tags, headers) {
  const p = json(token, body, headers);
  return http.post(`${BASE}${path}`, p.body, { headers: p.headers, tags });
}

function newClaim(token, amount) {
  const res = post('/api/v1/claims', token, {
    title: `Load test claim ${Date.now()}`,
    items: [{ category: 'FOOD', amount: amount, expenseDate: new Date(Date.now() - 86400000).toISOString().slice(0, 10), description: 'Lunch' }],
  }, { endpoint: 'create' });
  if (res.status !== 201) fail(`create claim failed: ${res.status} ${res.body}`);
  return res.json('claim.id');
}

export function setup() {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 1e6)}`;
  const reg = post('/api/v1/auth/register', null, {
    organizationName: `Load Org ${suffix}`, adminFullName: 'Load Admin',
    adminEmail: `admin-${suffix}@load.test`, adminPassword: 'LoadTest#123',
  }, { endpoint: 'setup' });
  if (reg.status !== 201) fail(`register failed: ${reg.status} ${reg.body}`);
  const adminToken = reg.json('accessToken');

  const mk = (email, name, role, managerId) => {
    const r = post('/api/v1/users', adminToken, { email, password: 'LoadTest#123', fullName: name, role, managerId }, { endpoint: 'setup' });
    if (r.status !== 201) fail(`create user failed: ${r.status} ${r.body}`);
    return r.json('id');
  };
  const login = (email) => {
    const r = post('/api/v1/auth/login', null, { email, password: 'LoadTest#123' }, { endpoint: 'setup' });
    if (r.status !== 200) fail(`login failed: ${r.status}`);
    return r.json('accessToken');
  };

  const managerEmail = `manager-${suffix}@load.test`;
  const managerId = mk(managerEmail, 'Load Manager', 'MANAGER', null);
  const employeeTokens = [];
  for (let i = 0; i < EMPLOYEES; i++) {
    const email = `emp${i}-${suffix}@load.test`;
    mk(email, `Employee ${i}`, 'EMPLOYEE', managerId);
    employeeTokens.push(login(email));
  }
  const managerToken = login(managerEmail);

  const claimIds = [];
  for (let i = 0; i < PRE_CLAIMS; i++) {
    const token = employeeTokens[i % EMPLOYEES];
    const id = newClaim(token, '300.00');
    const s = post(`/api/v1/claims/${id}/submit`, token, undefined, { endpoint: 'setup' }, { 'Idempotency-Key': `setup-${suffix}-${i}` });
    if (s.status !== 200) fail(`pre-submit failed: ${s.status} ${s.body}`);
    claimIds.push(id);
  }
  return { managerToken, employeeTokens, claimIds };
}

export function submitFlow(data) {
  const token = data.employeeTokens[(exec.vu.idInTest - 1) % data.employeeTokens.length];
  const id = newClaim(token, '250.00');
  const key = `k6-${exec.vu.idInTest}-${exec.vu.iterationInScenario}-${Date.now()}-${Math.random()}`;

  const res = post(`/api/v1/claims/${id}/submit`, token, undefined, { endpoint: 'submit' }, { 'Idempotency-Key': key });
  check(res, {
    'submit 200': (r) => r.status === 200,
    'status SUBMITTED': (r) => r.json('claim.status') === 'SUBMITTED',
  });

  if (exec.vu.iterationInScenario % 5 === 0) {      // simulate a client retry with the same key
    const again = post(`/api/v1/claims/${id}/submit`, token, undefined, { endpoint: 'submit_replay' }, { 'Idempotency-Key': key });
    const ok = check(again, {
      'replay 200': (r) => r.status === 200,
      'replay flagged': (r) => r.headers['Idempotent-Replay'] === 'true',
    });
    if (ok) replays.add(1);
  }
}

export function approveFlow(data) {
  const idx = exec.scenario.iterationInTest;
  const id = data.claimIds[idx];

  const res = post(`/api/v1/claims/${id}/approve`, data.managerToken, { comment: 'load test' }, { endpoint: 'approve' });
  check(res, {
    'approve 200': (r) => r.status === 200,
    'status MANAGER_APPROVED': (r) => r.json('claim.status') === 'MANAGER_APPROVED',
  });

  if (idx % 5 === 0) {                              // a second approval must be refused, never applied twice
    const dup = post(`/api/v1/claims/${id}/approve`, data.managerToken, { comment: 'duplicate' }, { endpoint: 'approve_duplicate' });
    const ok = check(dup, { 'duplicate approve is 409': (r) => r.status === 409 });
    if (ok) doubleApproveRejected.add(1);
  }
}
