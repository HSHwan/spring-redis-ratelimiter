package com.keeper.ratelimiter.aop;

import com.keeper.ratelimiter.annotation.RateLimit;
import com.keeper.ratelimiter.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

  private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String UNKNOWN_IP = "unknown";
  private static final String EXCEPTION_MESSAGE = "Too many requests. Please try again later.";
  private static final String REQUEST_TOKEN_COUNT = "1"; // Lua 스크립트로 보낼 요청 토큰 수
  private static final int MILLI_TO_SECOND_DIVISOR = 1000; // 밀리초 -> 초 변환 나누기 값

  private final StringRedisTemplate redisTemplate;
  private final RedisScript<Boolean> rateLimitScript;

  @Around("@annotation(rateLimit)")
  public Object handleRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
    String key = generateRateLimitKey(joinPoint);

    if (!tryAcquireToken(key, rateLimit)) {
      throw new RateLimitException(EXCEPTION_MESSAGE);
    }

    return joinPoint.proceed();
  }

  /**
   * Rate Limiter 검증을 위한 고유 키를 생성합니다.
   */
  private String generateRateLimitKey(ProceedingJoinPoint joinPoint) {
    String clientIp = getClientIp();
    String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
    return RATE_LIMIT_KEY_PREFIX + clientIp + ":" + methodName;
  }

  /**
   * Redis Lua Script를 실행하여 토큰 차감 가능 여부를 확인합니다.
   */
  private boolean tryAcquireToken(String key, RateLimit rateLimit) {
    double rate = (double) rateLimit.limit() / rateLimit.period();
    long now = System.currentTimeMillis() / MILLI_TO_SECOND_DIVISOR;
    List<String> keys = Collections.singletonList(key);

    Boolean allowed = redisTemplate.execute(
        rateLimitScript,
        keys,
        String.valueOf(rateLimit.limit()),
        String.valueOf(rate),
        String.valueOf(now),
        REQUEST_TOKEN_COUNT
    );

    return Boolean.TRUE.equals(allowed);
  }

  /**
   * HTTP 요청에서 클라이언트의 IP 주소를 추출합니다.
   */
  private String getClientIp() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attributes == null) {
      return UNKNOWN_IP;
    }
    HttpServletRequest request = attributes.getRequest();
    String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
    if (ip == null || ip.isEmpty()) {
      ip = request.getRemoteAddr();
    }
    return ip;
  }
}
