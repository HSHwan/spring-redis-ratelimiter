package com.keeper.ratelimiter.controller;

import com.keeper.ratelimiter.annotation.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

  /**
   * 테스트용 API
   * 설정: 1초(period) 동안 최대 10번(limit) 요청 허용
   */
  @RateLimit(limit = 10, period = 1)
  @GetMapping("/api/test")
  public String test() {
    return "success";
  }
}