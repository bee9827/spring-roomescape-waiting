package roomescape.common.ratelimit;

import java.time.Duration;

/**
 * 대기 추상화. 운영은 Thread.sleep으로 실제 대기하고, 테스트는 즉시 반환하는 가짜를 주입해
 * 대기 없이 흐름을 결정적으로 검증한다(System.sleep을 박지 않는 이유 = 토큰 버킷의 가짜 시계와 같은 결).
 * 429 백오프 재시도(RetryAfterInterceptor)와 토큰 대기(TokenBucketRateLimiter)가 함께 쓴다.
 * 단위는 시그니처에 박지 않고 Duration으로 받는다 — 호출자마다 자연스러운 단위(초·ms·ns)가 달라서.
 */
@FunctionalInterface
public interface BackoffSleeper {

    void sleep(Duration duration);

    static BackoffSleeper realTime() {
        return duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("대기 중 인터럽트되었습니다.", e);
            }
        };
    }
}
