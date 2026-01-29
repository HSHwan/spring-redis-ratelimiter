import http from 'k6/http';
import { check } from 'k6';

export const options = {
  scenarios: {
    resilience_test: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 50,
      maxVUs: 100,
    },
  },
  thresholds: {
    'http_req_failed{status:500}': ['rate==0'],
    'checks': ['rate>0.9'], 
  },
};

export default function () {
  const res = http.get('http://localhost:8080/api/test/user-limit');

  check(res, {
    'Status is valid (200 or 429)': (r) => r.status === 200 || r.status === 429,
    'Service Available (Not 500)': (r) => r.status !== 500,
  });
}