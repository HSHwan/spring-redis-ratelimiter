import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    single_abuser: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
    },
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/test/user-limit');

  check(res, {
    'Allowed (200 OK)': (r) => r.status === 200,
    'Blocked (429 Too Many Requests)': (r) => r.status === 429,
  });
}