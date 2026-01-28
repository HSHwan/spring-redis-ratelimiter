package com.keeper.ratelimiter.aop;

import com.keeper.ratelimiter.annotation.RateLimit;
import com.keeper.ratelimiter.component.RateLimitKeyGenerator;
import com.keeper.ratelimiter.constant.RateLimitType;
import com.keeper.ratelimiter.exception.RateLimitException;
import com.keeper.ratelimiter.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

  private static final String USER_LIMIT_MESSAGE = "Too many requests. Please try again later.";
  private static final String GLOBAL_LIMIT_MESSAGE = "System is currently busy. Please try again later.";

  private final RateLimiterService rateLimiterService;
  private final RateLimitKeyGenerator keyGenerator;

  /**
   * @RateLimit 어노테이션이 선언된 메서드의 실행을 가로채어 트래픽 유량을 제어합니다.
   *
   * @param joinPoint AOP JoinPoint
   * @param rateLimit @RateLimit 어노테이션 정보
   * @return 비즈니스 로직 실행 결과
   * @throws RateLimitException 임계치를 초과한 경우 발생
   */
  @Around("@annotation(rateLimit)")
  public Object handleRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
    validateRateLimitConfig(rateLimit);

    if (rateLimit.globalLimit() > 0) {
      verifyGlobalLimit(joinPoint, rateLimit);
    }

    verifyUserLimit(joinPoint, rateLimit);

    return joinPoint.proceed();
  }

  private void verifyGlobalLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
    String globalKey = keyGenerator.generateGlobalKey(joinPoint);

    boolean allowed = rateLimiterService.tryAcquire(
        globalKey,
        rateLimit.globalLimit(),
        rateLimit.globalPeriod(),
        RateLimitType.GLOBAL
    );

    if (!allowed) {
      log.warn("Global Limit Exceeded - Key: {}", globalKey);
      throw new RateLimitException(GLOBAL_LIMIT_MESSAGE);
    }
  }

  private void verifyUserLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) {
    String userKey = keyGenerator.generateKey(joinPoint);

    boolean allowed = rateLimiterService.tryAcquire(
        userKey,
        rateLimit.limit(),
        rateLimit.period(),
        RateLimitType.USER
    );

    if (!allowed) {
      log.warn("User Limit Exceeded - Key: {}", userKey);
      throw new RateLimitException(USER_LIMIT_MESSAGE);
    }
  }

  /**
   * 어노테이션 설정값의 유효성을 검증합니다.
   */
  private void validateRateLimitConfig(RateLimit rateLimit) {
    // User Limit 검증
    if (rateLimit.limit() < 0) {
      throw new IllegalArgumentException("User limit cannot be negative.");
    }
    if (rateLimit.period() <= 0) {
      throw new IllegalArgumentException("User period must be greater than 0.");
    }

    // Global Limit 검증
    if (rateLimit.globalLimit() < 0) {
      throw new IllegalArgumentException("Global limit cannot be negative.");
    }
    if (rateLimit.globalPeriod() <= 0) {
      throw new IllegalArgumentException("Global period must be greater than 0.");
    }
  }
}