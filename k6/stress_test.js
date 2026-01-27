import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '10s', target: 10 },  // 10초 동안 유저 10명으로 서서히 증가
    { duration: '30s', target: 100 }, // 30초 동안 유저 100명 유지 (부하 구간)
    { duration: '10s', target: 0 },   // 10초 동안 종료
  ],
};

export default function () {
  const url = 'http://localhost:8080/api/test';
  // 모든 유저가 '동일한 IP'로 공격한다고 가정 (DDoS 시뮬레이션)
  const params = {
    headers: { 'X-Forwarded-For': '203.0.113.1' },
  };

  const res = http.get(url, params);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });
}