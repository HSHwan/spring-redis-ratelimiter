import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  scenarios: {
    heavy_user: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      exec: 'heavyUser',
    },
    botnet: {
      executor: 'constant-arrival-rate',
      rate: 200,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
      exec: 'botnet',
    },
  },
};

export function heavyUser() {
  const res = http.get('http://localhost:8080/api/test/dual-limit');
  check(res, {
    'User: Allowed': (r) => r.status === 200,
    'User: Blocked': (r) => r.status === 429
  });
}

export function botnet() {
  const fakeIp = `1.1.${randomIntBetween(1, 255)}.${randomIntBetween(1, 255)}`;
  const params = { headers: { 'X-Forwarded-For': fakeIp } };
  const res = http.get('http://localhost:8080/api/test/dual-limit', params);
  check(res, {
    'Botnet: Allowed': (r) => r.status === 200,
    'Botnet: Blocked': (r) => r.status === 503 || r.status === 429
  });
}