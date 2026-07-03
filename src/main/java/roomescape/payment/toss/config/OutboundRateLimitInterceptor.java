package roomescape.payment.toss.config;

import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import roomescape.common.ratelimit.TokenBucketRateLimiter;
import roomescape.payment.exception.OutboundRateLimitException;

/**
 * 나가는 토스 호출에 Rate Limit을 거는 아웃바운드 인터셉터(클라이언트 관점). 들어오는 쪽(RateLimitInterceptor)과
 * 똑같은 TokenBucketRateLimiter를 방향만 바꿔 재사용한다 — 한도를 넘겨 호출하면 어차피 429로 거부당하니,
 * 보내기 전에 스스로 조절해 외부로 보내지 않는다.
 *
 * <p>거부 정책은 들어오는 쪽(즉시 거부)과 다르다 — 호출자가 내부 워커·결제 스레드로 유계라, 즉시 거부해
 * 재시도 왕복을 만드는 것보다 잠깐 붙잡고 기다리는 쪽이 싸다. 다만 무한 대기는 스레드를 하염없이 붙잡으므로
 * maxWait까지만 기다리고, 그래도 토큰이 없으면 OutboundRateLimitException으로 거부한다(기존 오류 경로 재사용).
 */
public class OutboundRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final TokenBucketRateLimiter rateLimiter;
    private final Duration maxWait;

    public OutboundRateLimitInterceptor(TokenBucketRateLimiter rateLimiter, Duration maxWait) {
        this.rateLimiter = rateLimiter;
        this.maxWait = maxWait;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (!rateLimiter.tryConsume(maxWait)) {
            throw new OutboundRateLimitException("나가는 결제 호출이 한도를 초과했고 대기 상한 안에 풀리지 않아 외부로 보내지 않았습니다.");
        }
        return execution.execute(request, body);
    }
}
