import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  // 50명의 가상 유저가 동시에 접속
  vus: 50,
  duration: '30s',
};

export default function () {
  const url = 'http://localhost:8080/api/test';

  // 각 가상 유저(VU)마다 고유한 IP 생성 (1.1.1.1 ~ 1.1.1.50)
  const fakeIp = `1.1.1.${__VU}`;

  const params = {
    headers: {
      'X-Forwarded-For': fakeIp,
    },
  };

  const res = http.get(url, params);

  // 각 유저는 "자신만의 토큰 버킷"을 가져야 하므로,
  // 과도하게 요청하지 않는 이상(여기선 sleep 1초) 모두 200이어야 함.
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

  // 1초에 1번 요청 (Limit이 5라면 절대 차단되면 안 됨)
  sleep(1);
}