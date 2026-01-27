package com.keeper.ratelimiter.component;

import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitKeyGeneratorTest {

  private RateLimitKeyGenerator keyGenerator;

  @Mock
  private ProceedingJoinPoint joinPoint;

  @Mock
  private MethodSignature signature;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    MockitoAnnotations.openMocks(this);
    keyGenerator = new RateLimitKeyGenerator();

    Method method = this.getClass().getMethod("dummyMethod");

    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
  }

  public void dummyMethod() {}

  @Test
  @DisplayName("로그인한 사용자는 'user:{ID}' 기반의 키가 생성되어야 한다")
  void generateKey_User() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setUserPrincipal(() -> "keeper_student");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).contains("user:keeper_student");
    assertThat(key).contains("dummyMethod");
  }

  @Test
  @DisplayName("비로그인 사용자는 'ip:{IP}' 기반의 키가 생성되어야 한다")
  void generateKey_Guest() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.0.1"); // IP 설정
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).contains("ip:192.168.0.1");
    assertThat(key).doesNotContain("user:");
  }

  @Test
  @DisplayName("프록시 환경: X-Forwarded-For 헤더가 있으면 해당 IP를 식별자로 사용해야 한다")
  void generateKey_WithXForwardedFor() {
    // Given
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Forwarded-For", "203.0.113.195"); // 프록시 IP
    request.setRemoteAddr("192.168.0.1"); // 로드밸런서 IP (무시되어야 함)
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).contains("ip:203.0.113.195"); // 헤더 값이 우선순위
  }

  @Test
  @DisplayName("예외 상황: RequestContext가 없는 경우(HTTP 요청 아님) 'unknown'으로 처리해야 한다")
  void generateKey_NoRequestContext() {
    // Given
    RequestContextHolder.resetRequestAttributes(); // 컨텍스트 강제 초기화

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    // ip:unknown으로 떨어지는지 확인 (NullPointerException 방지 로직 검증)
    assertThat(key).contains("ip:unknown");
  }
}