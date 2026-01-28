import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  scenarios: {
    ddos_attack: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },  // 100개의 공격 스레드 투입
        { duration: '30s', target: 100 },  // 공격 지속
        { duration: '10s', target: 0 },    // 공격 종료
      ],
    },
  },
};

export default function () {
  // 랜덤 IP 생성 (Botnet Simulation)
  // 1.0.0.0 ~ 255.255.255.255 사이의 랜덤 IP 생성
  const fakeIp = `${randomIntBetween(1, 255)}.${randomIntBetween(0, 255)}.${randomIntBetween(0, 255)}.${randomIntBetween(0, 255)}`;

  // 헤더 변조하여 전송
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Forwarded-For': fakeIp, // <--- 핵심: 매 요청마다 다른 IP인 척 위장
    },
  };

  // 공격 대상 URL
  const res = http.get('http://localhost:8080/api/test', params);

  check(res, {
    'status is 200 or 429': (r) => r.status === 200 || r.status === 429,
  });

  // 공격 속도 조절
  sleep(0.01);
}