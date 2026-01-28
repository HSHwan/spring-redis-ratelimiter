package com.keeper.ratelimiter.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RateLimitType {
  USER("user"),
  GLOBAL("global");

  private final String metricsValue;
}