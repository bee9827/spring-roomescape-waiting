package roomescape.payment.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import roomescape.common.ratelimit.TokenBucketRateLimiter;
import roomescape.payment.exception.OutboundRateLimitException;
import roomescape.payment.toss.config.OutboundRateLimitInterceptor;

/**
 * 나가는 호출 Rate Limit 인터셉터가 토큰 유무에 따라 실제 전송을 진행/대기/차단하는지 검증한다.
 * 토큰이 없으면 maxWait까지 기다려 보고, 그래도 없으면 execution을 호출하지 않고
 * OutboundRateLimitException으로 거부해야 한다.
 */
class OutboundRateLimitInterceptorTest {

    @Test
    void 토큰이_있으면_실제_전송을_진행한다() throws IOException {
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger executed = new AtomicInteger();
        OutboundRateLimitInterceptor interceptor = new OutboundRateLimitInterceptor(
                new TokenBucketRateLimiter(1, 1, clock::get), Duration.ofSeconds(2));

        interceptor.intercept(new MockClientHttpRequest(), new byte[0],
                (req, body) -> {
                    executed.incrementAndGet();
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                });

        assertThat(executed.get()).isEqualTo(1);
    }

    @Test
    void 토큰이_없어도_상한_안에_차면_기다렸다가_전송한다() throws IOException {
        AtomicLong clock = new AtomicLong(0);
        List<Long> sleeps = new ArrayList<>();
        AtomicInteger executed = new AtomicInteger();
        // 자는 대신 가짜 시계를 전진시키는 sleeper — 대기 후 성공 경로를 실제 시간 없이 재현.
        OutboundRateLimitInterceptor interceptor = new OutboundRateLimitInterceptor(
                new TokenBucketRateLimiter(1, 1, clock::get, millis -> {
                    sleeps.add(millis);
                    clock.addAndGet(millis * 1_000_000L);
                }), Duration.ofSeconds(2));
        // 유일한 토큰을 첫 호출이 소비한다.
        interceptor.intercept(new MockClientHttpRequest(), new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        interceptor.intercept(new MockClientHttpRequest(), new byte[0],
                (req, body) -> {
                    executed.incrementAndGet();
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                });

        assertThat(executed.get()).isEqualTo(1); // 거부되지 않고
        assertThat(sleeps).containsExactly(1000L); // 토큰이 찰 때까지(1초) 기다렸다 전송했다
    }

    @Test
    void 상한_안에_토큰이_찰_수_없으면_외부로_보내지_않고_OutboundRateLimitException으로_거부한다() throws IOException {
        AtomicLong clock = new AtomicLong(0);
        AtomicInteger executed = new AtomicInteger();
        OutboundRateLimitInterceptor interceptor = new OutboundRateLimitInterceptor(
                new TokenBucketRateLimiter(1, 1, clock::get), Duration.ofMillis(500)); // 토큰은 1초 뒤에나 참
        // 유일한 토큰을 첫 호출이 소비한다.
        interceptor.intercept(new MockClientHttpRequest(), new byte[0],
                (req, body) -> new MockClientHttpResponse(new byte[0], HttpStatus.OK));

        assertThatThrownBy(() -> interceptor.intercept(new MockClientHttpRequest(), new byte[0],
                (req, body) -> {
                    executed.incrementAndGet();
                    return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
                }))
                .isInstanceOf(OutboundRateLimitException.class);

        assertThat(executed.get()).isZero(); // 두 번째 호출은 execution이 실행되지 않았다
    }
}
