package com.keeper.ratelimiter.service;

import com.keeper.ratelimiter.constant.RateLimitType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private RedisScript<Boolean> rateLimitScript;

  @Mock
  private MeterRegistry meterRegistry;

  @Mock
  private Counter counter;

  @InjectMocks
  private RateLimiterService rateLimiterService;

  @BeforeEach
  void setUp() {
    lenient().when(meterRegistry.counter(any(), any(String[].class))).thenReturn(counter);
  }

  @Test
  @DisplayName("Redis 연결 실패 시 예외가 발생하지 않고 요청을 허용(true)해야 한다")
  void tryAcquire_FailOpen() {
    // Given
    // Redis가 통신 중 예외를 던지도록 설정
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any(), any()))
        .thenThrow(new RedisConnectionFailureException("Redis Connection Refused"));

    // When
    boolean allowed = rateLimiterService.tryAcquire("test:key", 10, 1, RateLimitType.USER);

    // Then
    // 차단(false)되거나 에러(Exception)가 터지면 안 되고, 통과(true)되어야 함
    assertThat(allowed).isTrue();
  }

  @Test
  @DisplayName("Redis 스크립트가 true를 반환하면(허용), 결과도 true여야 한다")
  void tryAcquire_Allowed() {
    // Given
    // Redis가 true(허용)를 반환하도록 Mocking
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any(), any()))
        .thenReturn(true);

    // When
    boolean result = rateLimiterService.tryAcquire("test:key", 10, 1, RateLimitType.USER);

    // Then
    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("Redis 스크립트가 false를 반환하면(차단), 결과도 false여야 한다")
  void tryAcquire_Blocked() {
    // Given
    // Redis가 false(차단)를 반환하도록 Mocking
    when(redisTemplate.execute(any(RedisScript.class), any(List.class), any(), any(), any(), any()))
        .thenReturn(false);

    // When
    boolean result = rateLimiterService.tryAcquire("test:key", 10, 1, RateLimitType.USER);

    // Then
    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("Enum 타입이 GLOBAL이면 메트릭 태그에 'type=global'이 기록되어야 한다.")
  void shouldRecordGlobalTag_WhenEnumIsGlobal() {
    // Given
    when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
        .thenReturn(true);

    // When
    rateLimiterService.tryAcquire("key", 10, 1, RateLimitType.GLOBAL);

    // Then
    verify(meterRegistry).counter(
        eq("ratelimiter.request"),
        eq("result"), eq("allowed"),
        eq("type"), eq("global")
    );
  }

  @Test
  @DisplayName("Enum 타입이 USER면 메트릭 태그에 'type=user'가 기록되어야 한다.")
  void shouldRecordUserTag_WhenEnumIsUser() {
    // Given
    when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
        .thenReturn(false);

    // When
    rateLimiterService.tryAcquire("key", 10, 1, RateLimitType.USER);

    // Then
    verify(meterRegistry).counter(
        eq("ratelimiter.request"),
        eq("result"), eq("blocked"),
        eq("type"), eq("user") // 핵심 검증 포인트
    );
  }
}