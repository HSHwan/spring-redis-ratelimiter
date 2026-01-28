package com.keeper.ratelimiter.component;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;

@Component
public class RateLimitKeyGenerator {

  private static final String RATE_LIMIT_PREFIX = "rate_limit:";
  private static final String GLOBAL_PREFIX = "global:"; // 추가
  private static final String USER_PREFIX = "user:";
  private static final String IP_PREFIX = "ip:";
  private static final String KEY_DELIMITER = ":";
  private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String UNKNOWN_IP = "unknown";

  /**
   * Rate Limiter 검증을 위한 고유 키를 생성합니다.
   * - 로그인 사용자: User ID를 사용하여 IP 우회 공격을 방어합니다.
   * - 비로그인 사용자: IP Address를 사용하여 식별합니다.
   *
   * @param joinPoint AOP JoinPoint
   * @return 생성된 Redis Key (예: rate_limit:user:keeper123:methodName)
   */
  public String generateKey(ProceedingJoinPoint joinPoint) {
    HttpServletRequest request = getRequest();
    String methodName = getMethodName(joinPoint);

    String identifier;
    String prefix;

    Principal userPrincipal = (request != null) ? request.getUserPrincipal() : null;

    if (userPrincipal != null) {
      identifier = userPrincipal.getName();
      prefix = USER_PREFIX;
    } else {
      identifier = getClientIp(request);
      prefix = IP_PREFIX;
    }

    return RATE_LIMIT_PREFIX
        + prefix
        + identifier
        + KEY_DELIMITER
        + methodName;
  }

  /**
   * Global Rate Limiting을 위한 키를 생성합니다.
   * 사용자 식별자(IP/User ID)를 제외하고 메서드 이름만 사용합니다.
   * @param joinPoint AOP JoinPoint
   * @return 생성된 Redis Key (예: rate_limit:global:searchProducts)
   */
  public String generateGlobalKey(ProceedingJoinPoint joinPoint) {
    String methodName = getMethodName(joinPoint);

    return RATE_LIMIT_PREFIX
        + GLOBAL_PREFIX
        + methodName;
  }

  /**
   * 메서드 이름을 추출합니다.
   */
  private String getMethodName(ProceedingJoinPoint joinPoint) {
    return ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
  }

  /**
   * 현재 스레드의 HttpServletRequest 객체를 가져옵니다.
   */
  private HttpServletRequest getRequest() {
    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    return attributes != null ? attributes.getRequest() : null;
  }

  /**
   * HTTP 요청 헤더에서 클라이언트의 실제 IP 주소를 추출합니다.
   */
  private String getClientIp(HttpServletRequest request) {
    if (request == null) return UNKNOWN_IP;
    String ip = request.getHeader(HEADER_X_FORWARDED_FOR);
    return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
  }
}