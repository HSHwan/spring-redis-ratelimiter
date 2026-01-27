package com.keeper.ratelimiter;

import com.keeper.ratelimiter.annotation.RateLimit;
import com.keeper.ratelimiter.exception.RateLimitException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
@Import(RateLimiterIntegrationTest.DummyService.class)
class RateLimiterIntegrationTest {

	@Autowired
	private DummyService dummyService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@BeforeEach
	void setUp() {
		redisTemplate.delete(redisTemplate.keys("rate_limit:*"));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	@Test
	@DisplayName("[동시성] 1초에 5회 제한 시, 10개 요청 중 5개는 성공하고 5개는 차단되어야 한다")
	void concurrencyTest() throws InterruptedException {
		// Given
		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		CountDownLatch latch = new CountDownLatch(threadCount);

		AtomicInteger successCount = new AtomicInteger();
		AtomicInteger failCount = new AtomicInteger();

		// When
		for (int i = 0; i < threadCount; i++) {
			executorService.submit(() -> {
				try {
					// 비로그인 상태로 가정 (IP 기반 키 생성)
					dummyService.process();
					successCount.incrementAndGet();
				} catch (RateLimitException e) {
					failCount.incrementAndGet();
				} finally {
					latch.countDown();
				}
			});
		}

		latch.await();

		// Then
		assertThat(successCount.get()).isEqualTo(5);
		assertThat(failCount.get()).isEqualTo(5);
	}
	@Test
	@DisplayName("[토큰 충전] 제한 시간이 지나면(Refill) 다시 요청이 성공해야 한다")
	void refillTest() throws InterruptedException {
		// Given
		dummyService.processShortPeriod();
		dummyService.processShortPeriod();
		assertThatThrownBy(() -> dummyService.processShortPeriod())
				.isInstanceOf(RateLimitException.class);

		// When
		Thread.sleep(2500);

		// Then
		dummyService.processShortPeriod();
	}

	@Test
	@DisplayName("[키 독립성] 서로 다른 메서드는 Rate Limit가 독립적으로 적용되어야 한다")
	void independentMethodTest() {
		// Given
		for (int i = 0; i < 5; i++) {
			dummyService.process();
		}
		assertThatThrownBy(() -> dummyService.process())
				.isInstanceOf(RateLimitException.class);

		// When & Then
		dummyService.anotherProcess();
	}

	@Test
	@DisplayName("[비로그인 사용자 독립성] 서로 다른 IP(사용자)는 Rate Limit가 독립적으로 적용되어야 한다")
	void independentUserTest() {
		// Given
		setupRequestIp("127.0.0.1");
		for (int i = 0; i < 5; i++) {
			dummyService.process();
		}
		assertThatThrownBy(() -> dummyService.process())
				.isInstanceOf(RateLimitException.class);

		// When
		setupRequestIp("192.168.0.5");

		// Then
		dummyService.process();
	}

	@Test
	@DisplayName("[로그인 사용자 독립성] IP가 같아도 로그인 ID가 다르면 Rate Limit가 독립적으로 적용되어야 한다")
	void independentLoginUserTest() {
		// Given
		setupLoggedInUser("keeper_member");
		for (int i = 0; i < 5; i++) {
			dummyService.process();
		}
		assertThatThrownBy(() -> dummyService.process())
				.isInstanceOf(RateLimitException.class);

		// When
		setupLoggedInUser("new_member");

		// Then
		dummyService.process();
	}

	@Test
	@DisplayName("[프록시 IP 식별] X-Forwarded-For 헤더가 있으면 해당 IP를 기준으로 Rate Limit가 적용되어야 한다")
	void proxyIpTest() {
		// Given
		setupProxyRequest("1.2.3.4");
		for (int i = 0; i < 5; i++) {
			dummyService.process();
		}
		assertThatThrownBy(() -> dummyService.process())
				.isInstanceOf(RateLimitException.class);

		// When
		setupProxyRequest("5.6.7.8");

		// Then
		dummyService.process();
	}

	// 프록시 요청 헬퍼 메서드
	private void setupProxyRequest(String realIp) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.0.0.1"); // 로드밸런서 IP (모든 요청이 동일하다고 가정)
		request.addHeader("X-Forwarded-For", realIp); // 실제 클라이언트 IP
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	// 로그인 사용자 세팅을 위한 헬퍼 메서드
	private void setupLoggedInUser(String username) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1"); // IP는 동일하게 설정 (IP 무시 여부 검증)
		request.setUserPrincipal(() -> username); // Principal 주입 (로그인 상태)
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	// IP 변경을 위한 헬퍼 메서드
	private void setupRequestIp(String ip) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(ip);
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
	}

	/**
	 * AOP 테스트를 위한 내부 더미 서비스
	 */
	@Service
	static class DummyService {

		@RateLimit(limit = 5, period = 1)
		public void process() {
		}

		// Method Independence Test
		@RateLimit(limit = 5, period = 1)
		public void anotherProcess() {
		}

		// Refill Test
		@RateLimit(limit = 2, period = 2) // 2초에 2회
		public void processShortPeriod() {
		}
	}
}