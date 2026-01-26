package com.keeper.ratelimiter.service;

import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

  private static final String REQUEST_TOKEN_COUNT = "1";
  private static final int MILLI_TO_SECOND_DIVISOR = 1000;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Boolean> rateLimitScript;

  /**
   * Redis를 통해 토큰 획득을 시도합니다. (Fail-Open 정책 적용)
   *
   * @param key    Rate Limit Key
   * @param limit  허용 량
   * @param period 기간 (초)
   * @return 요청 허용 여부 (true: 허용, false: 차단)
   */
  public boolean tryAcquire(String key, int limit, int period) {
    try {
      double rate = (double) limit / period;
      long now = System.currentTimeMillis() / MILLI_TO_SECOND_DIVISOR;
      List<String> keys = Collections.singletonList(key);

      Boolean allowed = redisTemplate.execute(
          rateLimitScript,
          keys,
          String.valueOf(limit),
          String.valueOf(rate),
          String.valueOf(now),
          REQUEST_TOKEN_COUNT
      );
      return Boolean.TRUE.equals(allowed);
    } catch (Exception e) {
      // Redis 장애 시 서비스 가용성을 위해 무조건 통과
      log.error("Redis Rate Limiter Failure - Key: {}, Error: {}", key, e.getMessage());
      return true;
    }
  }
}