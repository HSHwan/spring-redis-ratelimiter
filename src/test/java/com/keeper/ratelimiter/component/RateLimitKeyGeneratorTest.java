package com.keeper.ratelimiter.component;

import java.lang.reflect.Method;
import java.security.Principal;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitKeyGeneratorTest {

  private RateLimitKeyGenerator keyGenerator;

  private ProceedingJoinPoint joinPoint;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    keyGenerator = new RateLimitKeyGenerator();

    joinPoint = mock(ProceedingJoinPoint.class);
    MethodSignature signature = mock(MethodSignature.class);
    Method method = mock(Method.class);

    when(joinPoint.getSignature()).thenReturn(signature);
    when(signature.getMethod()).thenReturn(method);
    when(method.getName()).thenReturn("testMethod"); // 타겟 메서드 이름

    request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
  }

  @AfterEach
  void tearDown() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("Global Key는 사용자 식별자 없이 'rate_limit:global:메서드명' 형식을 따라야 한다.")
  void shouldGenerateCorrectGlobalKey() {
    // When
    String globalKey = keyGenerator.generateGlobalKey(joinPoint);

    // Then
    assertThat(globalKey).isEqualTo("rate_limit:global:testMethod");
  }

  @Test
  @DisplayName("로그인 사용자는 'rate_limit:user:아이디:메서드명' 형식을 따라야 한다.")
  void shouldGenerateUserKey_WhenPrincipalExists() {
    // Given
    Principal principal = mock(Principal.class);
    when(principal.getName()).thenReturn("keeper123");
    request.setUserPrincipal(principal);

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).isEqualTo("rate_limit:user:keeper123:testMethod");
  }

  @Test
  @DisplayName("비로그인 사용자는 'rate_limit:ip:IP주소:메서드명' 형식을 따라야 한다.")
  void shouldGenerateIpKey_WhenPrincipalIsNull() {
    // Given
    request.setRemoteAddr("127.0.0.1"); // 기본 IP 설정

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).isEqualTo("rate_limit:ip:127.0.0.1:testMethod");
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더가 있으면 해당 IP를 사용해야 한다.")
  void shouldPrioritizeXForwardedForHeader() {
    // Given
    request.setRemoteAddr("10.0.0.1"); // 로드밸런서 IP (무시되어야 함)
    request.addHeader("X-Forwarded-For", "203.0.113.5"); // 실제 클라이언트 IP

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).isEqualTo("rate_limit:ip:203.0.113.5:testMethod");
  }

  @Test
  @DisplayName("X-Forwarded-For 헤더가 비어있으면 RemoteAddr를 사용해야 한다.")
  void shouldUseRemoteAddr_WhenHeaderIsEmpty() {
    // Given
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Forwarded-For", "");

    // When
    String key = keyGenerator.generateKey(joinPoint);

    // Then
    assertThat(key).isEqualTo("rate_limit:ip:127.0.0.1:testMethod");
  }
}