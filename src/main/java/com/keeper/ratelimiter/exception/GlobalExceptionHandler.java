package com.keeper.ratelimiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(RateLimitException.class)
  public ResponseEntity<String> handleRateLimitException(RateLimitException e) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .body(e.getMessage());
  }
}
