package com.keeper.ratelimiter.service;

import com.keeper.ratelimiter.constant.RateLimitType;
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
  private final MeterRegistry meterRegistry; // 레지스트리를 직접 사용

  public RateLimiterService(StringRedisTemplate redisTemplate,
      RedisScript<Boolean> rateLimitScript,
      MeterRegistry meterRegistry) {
    this.redisTemplate = redisTemplate;
    this.rateLimitScript = rateLimitScript;
    this.meterRegistry = meterRegistry;
  }

  /**
   * Redis를 통해 토큰 획득을 시도합니다. (Fail-Open 정책 적용)
   *
   * @param key       Rate Limit Key
   * @param limit     허용 량
   * @param period    기간 (초)
   * @param limitType 제한 유형 ("user" 또는 "global") - 메트릭 태그용
   * @return 요청 허용 여부 (true: 허용, false: 차단)
   */
  public boolean tryAcquire(String key, int limit, int period, RateLimitType limitType) {
    try {
      Boolean allowed = executeRateLimitScript(key, limit, period);
      updateMetrics(allowed, limitType);
      return Boolean.TRUE.equals(allowed);

    } catch (Exception e) {
      handleRedisFailure(key, e);
      return true;
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
   * 결과에 따라 동적으로 태그를 붙여 메트릭을 업데이트합니다.
   * Tag: result=(allowed/blocked), type=(user/global)
   */
  private void updateMetrics(Boolean allowed, RateLimitType limitType) {
    String result = Boolean.TRUE.equals(allowed) ? "allowed" : "blocked";

    meterRegistry.counter("ratelimiter.request",
        "result", result,
        "type", limitType.getMetricsValue()
    ).increment();
  }
  /**
   * Rate Limiting 도중 장애에 대한 예외를 처리합니다.
   */
  private void handleRedisFailure(String key, Exception e) {
    log.error("Redis Rate Limiter Failure - Key: {}, Error: {}", key, e.getMessage());
    meterRegistry.counter("ratelimiter.failure", "type", "redis_error").increment();
  }
}