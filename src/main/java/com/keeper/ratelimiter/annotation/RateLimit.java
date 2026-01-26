package com.keeper.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
  int limit() default 10;     // 최대 허용 횟수 (버킷 크기)
  int period() default 1;     // 기준 시간 (초 단위)
}