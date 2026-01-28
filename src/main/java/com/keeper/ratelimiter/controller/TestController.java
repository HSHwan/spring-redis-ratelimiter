package com.keeper.ratelimiter.controller;

import com.keeper.ratelimiter.annotation.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

  /**
   * [시나리오 1: User/IP Limit 검증]
   * - 상황: 한 명의 사용자가 빠르게 새로고침하는 상황
   * - 설정: 초당 5회 허용 (Global Limit은 꺼져있음)
   * - 기대 결과: 6번째 요청부터 429 에러 발생
   */
  @GetMapping("/user-limit")
  @RateLimit(limit = 5, period = 1) // Global Default = 0 (OFF)
  public ResponseEntity<String> testUserLimit() {
    return ResponseEntity.ok("User Limit Pass");
  }

  /**
   * [시나리오 2: Global Service Limit (DDoS) 검증]
   * - 상황: 봇넷(여러 IP)이 동시에 들어오지만, 개별 IP는 천천히 요청함
   * - 설정:
   * 1. User Limit: 1000 (사실상 무제한 - 개별 차단 안 당하게 설정)
   * 2. Global Limit: 50 (전체 합쳐서 초당 50회만 허용)
   * - 기대 결과:
   * 전체 트래픽이 100 RPS가 되면 -> 약 50개는 성공(200), 나머지 50개는 차단(503/429)
   */
  @GetMapping("/global-limit")
  @RateLimit(limit = 1000, period = 1, globalLimit = 50, globalPeriod = 1)
  public ResponseEntity<String> testGlobalLimit() {
    return ResponseEntity.ok("Global Limit Pass");
  }

  /**
   * [시나리오 3: Hybrid 검증]
   * - 상황: 실제 운영 환경 (헤비 유저도 막고, 전체 폭주도 막음)
   * - 설정:
   * 1. User Limit: 5 (도배 방지)
   * 2. Global Limit: 100 (서버 보호)
   * - 기대 결과:
   * 한 IP에서 10번 요청 -> 5번 성공, 5번 차단
   * 동시에 전체 트래픽이 200 RPS가 되면 -> 정상 유저들도 Global Limit에 걸려 차단됨
   */
  @GetMapping("/dual-limit")
  @RateLimit(limit = 5, period = 1, globalLimit = 100, globalPeriod = 1)
  public ResponseEntity<String> testDualLimit() {
    return ResponseEntity.ok("Dual Limit Pass");
  }
}