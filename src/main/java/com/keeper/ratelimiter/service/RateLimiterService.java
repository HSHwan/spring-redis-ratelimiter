package com.keeper.ratelimiter.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RateLimiterService {

  private static final String REQUEST_TOKEN_COUNT = "1";
  private static final int MILLI_TO_SECOND_DIVISOR = 1000;

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Boolean> rateLimitScript;

  // 메트릭 카운터
  private final Counter allowedCounter;
  private final Counter blockedCounter;
  private final Counter failOpenCounter;

  public RateLimiterService(StringRedisTemplate redisTemplate,
      RedisScript<Boolean> rateLimitScript,
      MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.rateLimitScript = rateLimitScript;

    // 메트릭 등록
    this.allowedCounter = meterRegistry.counter("ratelimiter.request", "result", "allowed");
    this.blockedCounter = meterRegistry.counter("ratelimiter.request", "result", "blocked");
    this.failOpenCounter = meterRegistry.counter("ratelimiter.failure", "type", "redis_error");
  }

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
      Boolean allowed = executeRateLimitScript(key, limit, period);
      updateMetrics(allowed);
      return Boolean.TRUE.equals(allowed);

    } catch (Exception e) {
      handleRedisFailure(key, e);
      return true; // Fail-Open: 장애 시 무조건 허용
    }
  }

  /**
   * Redis Lua Script를 실행하여 토큰을 획득할 수 있는지 확인합니다.
   */
  private Boolean executeRateLimitScript(String key, int limit, int period) {
    double rate = (double) limit / period;
    long now = System.currentTimeMillis() / MILLI_TO_SECOND_DIVISOR;
    List<String> keys = Collections.singletonList(key);

    return redisTemplate.execute(
        rateLimitScript,
        keys,
        String.valueOf(limit),
        String.valueOf(rate),
        String.valueOf(now),
        REQUEST_TOKEN_COUNT
    );
  }

  /**
   * Redis Lua Script 실행 결과에 따라 메트릭을 업데이트합니다.
   */
  private void updateMetrics(Boolean allowed) {
    if (Boolean.TRUE.equals(allowed)) {
      allowedCounter.increment();
    } else {
      blockedCounter.increment();
    }
  }

  /**
   * Rate Limiting 도중 장애에 대한 예외를 처리합니다.
   */
  private void handleRedisFailure(String key, Exception e) {
    log.error("Redis Rate Limiter Failure - Key: {}, Error: {}", key, e.getMessage());
    failOpenCounter.increment();
  }
}