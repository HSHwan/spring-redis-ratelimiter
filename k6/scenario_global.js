import http from 'k6/http';
import { check } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  scenarios: {
    botnet_attack: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 100,
    },
  },
};

export default function () {
  const fakeIp = `203.0.${randomIntBetween(1, 255)}.${randomIntBetween(1, 255)}`;
  const params = { headers: { 'X-Forwarded-For': fakeIp } };

  const res = http.get('http://localhost:8080/api/test/global-limit', params);

  check(res, {
    'Allowed (200 OK)': (r) => r.status === 200,
    'Blocked (Global Limit)': (r) => r.status === 429 || r.status === 503,
  });
}