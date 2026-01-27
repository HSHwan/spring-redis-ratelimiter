import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 10,
  duration: '30s', // 30초 동안 실행
};

export default function () {
  const res = http.get('http://localhost:8080/api/test');

  // Redis가 죽으면 예외를 먹고 200(통과)을 리턴해야 함
  // 500 에러가 뜨면 Fail-Open 실패
  check(res, {
    'is working (200 or 429)': (r) => r.status === 200 || r.status === 429,
    'is not server error (500)': (r) => r.status !== 500,
  });

  sleep(0.1);
}