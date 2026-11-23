/**
 * k6 load test: GET /products (through the API gateway).
 *
 * Cache toggle:
 *   CACHE_ENABLED=true  (default) - warm/cached reads.
 *   CACHE_ENABLED=false            - send X-Cache-Bypass and tag results as
 *                                    "cache=off" for the cold/uncached run.
 *                                    Start inventory-service with
 *                                    INVENTORY_CACHE_ENABLED=false (Spring
 *                                    property inventory.cache.enabled=false)
 *                                    for a true no-Redis comparison.
 *
 * Run:
 *   k6 run -e JWT=<token> -e CACHE_ENABLED=true  load-tests/products.js
 *   k6 run -e JWT=<token> -e CACHE_ENABLED=false load-tests/products.js
 */
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_PATH = __ENV.LOGIN_PATH || '/auth/login';
const CACHE_MODE = (__ENV.CACHE_MODE || __ENV.CACHE_ENABLED || 'on').toString().toLowerCase();
const CACHE_ENABLED = !['off', 'false', '0', 'no', 'disabled'].includes(CACHE_MODE);
const PAGE = Number(__ENV.PAGE || 0);
const SIZE = Number(__ENV.PAGE_SIZE || 20);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

const errors = new Rate('errors');

export const options = {
  tags: {
    test: 'products',
    cache: CACHE_ENABLED ? 'on' : 'off',
  },
  stages: [
    { duration: __ENV.WARMUP_DURATION || '30s', target: Number(__ENV.WARMUP_VUS || 5) },
    { duration: __ENV.RAMP_DURATION || '1m', target: Number(__ENV.RAMP_VUS || 20) },
    { duration: __ENV.STEADY_DURATION || '2m', target: Number(__ENV.STEADY_VUS || 20) },
  ],
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<800'],
    errors: ['rate<0.05'],
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
  return { token: obtainToken() };
}

export default function (data) {
  const headers = {
    Authorization: `Bearer ${data.token}`,
    Accept: 'application/json',
  };
  if (!CACHE_ENABLED) {
    headers['X-Cache-Bypass'] = 'true';
  }

  const url = `${BASE_URL}/api/inventory/products?page=${PAGE}&size=${SIZE}`;
  const response = http.get(url, { headers, tags: { name: 'GET /products' } });

  const ok = check(response, {
    'GET /products returns 200': (r) => r.status === 200,
    'response is JSON list': (r) => r.headers['Content-Type'] && r.headers['Content-Type'].indexOf('json') >= 0,
  });
  errors.add(!ok);
  sleep(SLEEP_SECONDS);
}
