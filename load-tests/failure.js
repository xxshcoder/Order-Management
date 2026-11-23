/**
 * k6 failure-mode probe: run this while a dependency is stopped.
 *
 * Suggested procedure (see load-tests/README.md):
 *   docker compose stop inventory-service     # or: docker compose stop kafka
 *   k6 run -e JWT=<token> load-tests/failure.js
 *   docker compose start inventory-service
 *
 * The script intentionally has no failing thresholds: the goal is to record
 * behaviour (5xx, timeouts, latency) while the dependency is unavailable.
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_PATH = __ENV.LOGIN_PATH || '/auth/login';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 101);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 0.5);
const RUN_ID = __ENV.RUN_ID || String(Date.now());
const DISABLED_SERVICE = __ENV.DISABLED_SERVICE || 'inventory-service';

const failures = new Rate('dependency_failures');

export const options = {
  tags: {
    test: 'failure',
    disabled_service: DISABLED_SERVICE,
    run_id: RUN_ID,
  },
  scenarios: {
    failure_probe: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 5),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 5),
      maxVUs: Number(__ENV.MAX_VUS || 20),
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

function obtainToken() {
  if (__ENV.JWT) {
    return __ENV.JWT;
  }
  const payload = JSON.stringify({
    username: __ENV.LOGIN_USER || 'customer1',
    password: __ENV.LOGIN_PASSWORD || 'password',
  });
  const response = http.post(`${BASE_URL}${LOGIN_PATH}`, payload, {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /auth/login' },
  });
  const token = response.json('accessToken') || response.json('token') || response.json('access_token');
  if (!token) {
    throw new Error(`Login failed (${response.status}): ${response.body}`);
  }
  return token;
}

export function setup() {
  return { token: obtainToken(), runId: RUN_ID };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
  };

  let response;
  if (__ITER % 2 === 0) {
    headers['Idempotency-Key'] = `failure-${data.runId}-${__VU}-${__ITER}`;
    response = http.post(
      `${BASE_URL}/api/orders`,
      JSON.stringify({ items: [{ productId: PRODUCT_ID, quantity: 1 }] }),
      { headers, tags: { name: 'POST /orders', probe: 'order' } }
    );
  } else {
    response = http.get(`${BASE_URL}/api/inventory/products?page=0&size=20`, {
      headers,
      tags: { name: 'GET /products', probe: 'products' },
    });
  }

  const failed = response.timed_out || response.status >= 500 || response.status === 0;
  failures.add(failed);

  check(response, {
    'request received a response': (r) => r.status > 0,
    'failure is observable when dependency is down': (r) => r.status >= 500 || r.timed_out,
    'no unexpected client error (4xx)': (r) => r.status < 400 || r.status === 0,
  });

  sleep(SLEEP_SECONDS);
}
