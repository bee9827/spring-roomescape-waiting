package roomescape.payment.toss;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 나가는 토스 호출 Rate Limit 설정(클라이언트 관점). 들어오는 쪽(rate-limit.*)과 한도를 분리해 외부화한다.
 * capacity: 허용 버스트, refillPerSec: 평균 초당 호출 상한, maxWait: 토큰이 없을 때 기다려 줄 상한
 * (넘기면 거부 — 무한 대기로 스레드가 붙잡히는 것을 막는다).
 */
@ConfigurationProperties(prefix = "outbound-rate-limit")
public record OutboundRateLimitProperties(long capacity, double refillPerSec, Duration maxWait) {
}
