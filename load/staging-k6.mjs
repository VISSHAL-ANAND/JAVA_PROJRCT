import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.CAMPUSOS_STAGING_URL || '';
const EXPECTED_HOST = __ENV.CAMPUSOS_STAGING_HOST || '';
const APPROVAL = __ENV.CAMPUSOS_LOAD_TEST_APPROVAL || '';
const EMAIL_PREFIX = __ENV.CAMPUSOS_LOAD_TEST_EMAIL_PREFIX || '';
const EMAIL_DOMAIN = __ENV.CAMPUSOS_LOAD_TEST_EMAIL_DOMAIN || '';
const PASSWORD = __ENV.CAMPUSOS_LOAD_TEST_PASSWORD || '';
const RUN_ID = (__ENV.CAMPUSOS_LOAD_TEST_RUN_ID || 'manual').replace(/[^a-zA-Z0-9_-]/g, '_');

if (APPROVAL !== 'I_CONFIRM_THIS_IS_DEDICATED_STAGING') {
  throw new Error('Explicitly confirm a dedicated staging environment before running this test.');
}
if (!/^https:\/\/[^/]+$/.test(BASE_URL) || !EXPECTED_HOST || BASE_URL.slice(8) !== EXPECTED_HOST) {
  throw new Error('CAMPUSOS_STAGING_URL must be HTTPS and exactly match CAMPUSOS_STAGING_HOST.');
}
if (!EMAIL_PREFIX || !EMAIL_DOMAIN || !PASSWORD) {
  throw new Error('Provide disposable staging-account credentials through environment variables.');
}

const apiDuration = new Trend('api_duration', true);
const loginDuration = new Trend('login_duration', true);

export const options = {
  scenarios: {
    campusos_staging_capacity: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '2m', target: 500 },
        { duration: '3m', target: 1000 },
        { duration: '5m', target: 1000 },
        { duration: '2m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    api_duration: ['p(95)<2000'],
    login_duration: ['p(95)<10000'],
  },
};

let token = '';
let user = null;

function login() {
  const email = EMAIL_PREFIX + __VU + '@' + EMAIL_DOMAIN;
  const response = http.post(BASE_URL + '/api/auth/login',
    JSON.stringify({ email: email, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' }, tags: { name: 'POST /api/auth/login' } });
  loginDuration.add(response.timings.duration);
  const ok = check(response, {
    'login returns 200': (r) => r.status === 200,
    'login returns token and user id': (r) => Boolean(r.json('token')) && Number(r.json('user.id')) > 0,
  });
  if (!ok) {
    token = '';
    user = null;
    return false;
  }
  token = response.json('token');
  user = response.json('user');
  return true;
}

function apiGet(path, expectedKey) {
  const response = http.get(BASE_URL + path, {
    headers: { Authorization: 'Bearer ' + token, Accept: 'application/json' },
    tags: { name: 'GET ' + path.split('?')[0] },
  });
  apiDuration.add(response.timings.duration);
  check(response, {
    'authenticated GET returns 200': (r) => r.status === 200,
    ['response contains ' + expectedKey]: (r) => Boolean(r.json(expectedKey)),
  });
}

function submitLoadTestIssue() {
  const body = {
    title: 'CAMPUSOS_LOADTEST_' + RUN_ID + '_VU' + __VU + '_ITER' + __ITER,
    description: 'Dedicated staging capacity-test fixture. Safe to remove after the run.',
    category: 'LoadTest',
    location: 'Dedicated staging load-test fixture',
  };
  const response = http.post(BASE_URL + '/api/issues', JSON.stringify(body), {
    headers: { Authorization: 'Bearer ' + token, 'Content-Type': 'application/json' },
    tags: { name: 'POST /api/issues (load test)' },
  });
  apiDuration.add(response.timings.duration);
  check(response, {
    'issue submission returns 201': (r) => r.status === 201,
    'issue submission returns ticket id': (r) => Number(r.json('ticketId')) > 0,
  });
}

export default function () {
  if (!token || !user) {
    if (!login()) {
      sleep(1);
      return;
    }
  }

  const choice = __ITER % 20;
  if (choice === 0) {
    submitLoadTestIssue();
  } else if (choice % 3 === 0) {
    apiGet('/api/notifications/me', 'notifications');
  } else if (choice % 3 === 1) {
    apiGet('/api/resources', 'resources');
  } else {
    apiGet('/api/tickets/reporter/' + user.id + '?page=1', 'tickets');
  }
  sleep(5);
}
