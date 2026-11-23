/**
 * k6 load test: POST /orders ramp test.
 *
 * Every virtual user / iteration sends a stable, unique Idempotency-Key so
 * retries of the same logical request cannot create duplicate orders.
 *
 * Run:
 *   k6 run -e JWT=<token> -e PRODUCT_ID=101 -e RUN_ID=run-001 load-tests/orders.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_PATH = __ENV.LOGIN_PATH || '/auth/login';
const PRODUCT_ID = Number(__ENV.PRODUCT_ID || 101);
const QUANTITY = Number(__ENV.QUANTITY || 1);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);
const RUN_ID = __ENV.RUN_ID || String(Date.now());

const errors = new Rate('errors');
const orderLatency = new Trend('order_latency', true);

export const options = {
  tags: {
    test: 'orders',
    run_id: RUN_ID,
  },
  stages: [
    { duration: __ENV.WARMUP_DURATION || '30s', target: Number(__ENV.WARMUP_VUS || 2) },
    { duration: __ENV.RAMP_DURATION || '2m', target: Number(__ENV.RAMP_VUS || 25) },
    { duration: __ENV.STEADY_DURATION || '2m', target: Number(__ENV.STEADY_VUS || 25) },
    { duration: __ENV.RAMP_DOWN_DURATION || '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.10'],
    http_req_duration: ['p(95)<2000'],
    errors: ['rate<0.10'],
  },
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
  const idempotencyKey = `order-load-${data.runId}-${__VU}-${__ITER}`;
  const payload = JSON.stringify({
    items: [{ productId: PRODUCT_ID, quantity: QUANTITY }],
  });
  const headers = {
    Authorization: `Bearer ${data.token}`,
    'Content-Type': 'application/json',
    Accept: 'application/json',
    'Idempotency-Key': idempotencyKey,
    'X-Correlation-Id': idempotencyKey,
  };

  const response = http.post(`${BASE_URL}/api/orders`, payload, {
    headers,
    tags: { name: 'POST /orders' },
  });

  orderLatency.add(response.timings.duration);
  const ok = check(response, {
    'order accepted (201)': (r) => r.status === 201,
    'idempotent replay returns 200': (r) => r.status === 200,
    'response has an order id': (r) => {
      if (!r.body || r.body.length === 0) {
        return false;
      }
      try {
        const body = JSON.parse(r.body);
        return !!(body.id || body.orderId);
      } catch (e) {
        return false;
      }
    },
  });
  errors.add(!ok);
  sleep(SLEEP_SECONDS);
}
