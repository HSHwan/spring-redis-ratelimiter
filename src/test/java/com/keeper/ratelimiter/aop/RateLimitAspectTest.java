package com.keeper.ratelimiter.aop;

import com.keeper.ratelimiter.annotation.RateLimit;
import com.keeper.ratelimiter.component.RateLimitKeyGenerator;
import com.keeper.ratelimiter.exception.RateLimitException;
import com.keeper.ratelimiter.service.RateLimiterService;
import org.aspectj.lang.ProceedingJoinPoint;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitAspectTest {

  @Mock
  private RateLimiterService rateLimiterService;

  @Mock
  private RateLimitKeyGenerator keyGenerator;

  @Mock
  private ProceedingJoinPoint joinPoint;

  @InjectMocks
  private RateLimitAspect rateLimitAspect;

  private RateLimit rateLimitAnnotation;

  @BeforeEach
  void setUp() {
    rateLimitAnnotation = mock(RateLimit.class);

    lenient().when(rateLimitAnnotation.limit()).thenReturn(10);
    lenient().when(rateLimitAnnotation.period()).thenReturn(1);
    lenient().when(rateLimitAnnotation.globalLimit()).thenReturn(100);
    lenient().when(rateLimitAnnotation.globalPeriod()).thenReturn(1);

    lenient().when(keyGenerator.generateGlobalKey(any())).thenReturn("rate_limit:global:testMethod");
    lenient().when(keyGenerator.generateKey(any())).thenReturn("rate_limit:user:testUser:testMethod");
  }

  @Test
  @DisplayName("Global Limit을 초과하면 User Limit 검사 없이 즉시 예외가 발생해야 한다.")
  void shouldThrowException_WhenGlobalLimitExceeded() throws Throwable {
    // Given
    when(rateLimiterService.tryAcquire(eq("rate_limit:global:testMethod"), anyInt(), anyInt()))
        .thenReturn(false);

    // When & Then
    assertThatThrownBy(() -> rateLimitAspect.handleRateLimit(joinPoint, rateLimitAnnotation))
        .isInstanceOf(RateLimitException.class)
        .hasMessage("System is currently busy. Please try again later.");

    verify(rateLimiterService, never()).tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt());
    verify(joinPoint, never()).proceed();
  }

  @Test
  @DisplayName("Global Limit은 통과했지만 User Limit을 초과하면 예외가 발생해야 한다.")
  void shouldThrowException_WhenUserLimitExceeded() throws Throwable {
    // Given
    when(rateLimiterService.tryAcquire(eq("rate_limit:global:testMethod"), anyInt(), anyInt()))
        .thenReturn(true);
    when(rateLimiterService.tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt()))
        .thenReturn(false);

    // When & Then
    assertThatThrownBy(() -> rateLimitAspect.handleRateLimit(joinPoint, rateLimitAnnotation))
        .isInstanceOf(RateLimitException.class)
        .hasMessage("Too many requests. Please try again later.");

    verify(rateLimiterService).tryAcquire(eq("rate_limit:global:testMethod"), anyInt(), anyInt());
    verify(rateLimiterService).tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt());

    verify(joinPoint, never()).proceed();
  }

  @Test
  @DisplayName("Global Limit과 User Limit 모두 통과하면 비즈니스 로직이 실행되어야 한다.")
  void shouldProceed_WhenBothLimitsPassed() throws Throwable {
    // Given
    when(rateLimiterService.tryAcquire(eq("rate_limit:global:testMethod"), anyInt(), anyInt()))
        .thenReturn(true);
    when(rateLimiterService.tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt()))
        .thenReturn(true);

    // When
    rateLimitAspect.handleRateLimit(joinPoint, rateLimitAnnotation);

    // Then
    verify(joinPoint, times(1)).proceed();
  }

  @Test
  @DisplayName("Global Limit이 0(비활성)이면 Global 검사를 건너뛰어야 한다.")
  void shouldSkipGlobalCheck_WhenGlobalLimitIsZero() throws Throwable {
    // Given
    when(rateLimitAnnotation.globalLimit()).thenReturn(0); // 비활성화
    when(rateLimiterService.tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt()))
        .thenReturn(true);

    // When
    rateLimitAspect.handleRateLimit(joinPoint, rateLimitAnnotation);

    // Then
    verify(keyGenerator, never()).generateGlobalKey(any());
    verify(rateLimiterService, never()).tryAcquire(eq("rate_limit:global:testMethod"), anyInt(), anyInt());

    verify(rateLimiterService).tryAcquire(eq("rate_limit:user:testUser:testMethod"), anyInt(), anyInt());
    verify(joinPoint).proceed();
  }

  @Test
  @DisplayName("설정값이 유효하지 않으면 IllegalArgumentException이 발생해야 한다.")
  void shouldThrowException_WhenConfigIsInvalid() {
    // Given
    when(rateLimitAnnotation.limit()).thenReturn(-1);

    // When & Then
    assertThatThrownBy(() -> rateLimitAspect.handleRateLimit(joinPoint, rateLimitAnnotation))
        .isInstanceOf(IllegalArgumentException.class);
  }
}