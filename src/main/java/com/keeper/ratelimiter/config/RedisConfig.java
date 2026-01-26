package com.keeper.ratelimiter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

@Configuration
public class RedisConfig {
  
  private static final String RATELIMIT_SCRIPT_PATH = "scripts/ratelimit.lua";

  @Bean
  public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
    return new StringRedisTemplate(redisConnectionFactory);
  }

  @Bean
  public RedisScript<Boolean> rateLimitScript() {
    DefaultRedisScript<Boolean> script = new DefaultRedisScript<>();
    script.setLocation(new ClassPathResource(RATELIMIT_SCRIPT_PATH));
    script.setResultType(Boolean.class);
    return script;
  }
}