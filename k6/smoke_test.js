import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 1명의 가상 유저가 10초 동안 실행
  vus: 1,
  duration: '10s',
};

export default function () {
  // 테스트할 API 엔드포인트
  const url = 'http://localhost:8080/api/test';

  const res = http.get(url);

  // 결과 체크
  check(res, {
    'is status 200 (Allowed)': (r) => r.status === 200,
    'is status 429 (Blocked)': (r) => r.status === 429,
  });

  // 매우 빠른 속도로 요청 (0.05초 대기 -> 초당 약 20회 요청 시도)
  sleep(0.05);
};