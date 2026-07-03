package roomescape.common.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BlockingStrategy;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * 토큰 버킷 Rate Limiter — Bucket4j 어댑터(실험). capacity만큼 토큰이 차 있고 매초 refillPerSec개씩
 * 보충되며, 거부 정책은 호출자가 고른다 — 즉시 거부(tryConsume())와 상한 내 대기(tryConsume(maxWait)).
 *
 * <p>직접 구현(synchronized 토큰 버킷)을 공개 API는 그대로 둔 채 Bucket4j로 갈아끼운 버전.
 * 내부는 불변 상태 + AtomicReference CAS(lock-free)다. 시계(TimeMeter)와 대기(BlockingStrategy)를
 * 주입받는 구조라, 기존의 가짜 시계·가짜 sleeper 테스트가 그대로 통과해야 한다.
 */
public class TokenBucketRateLimiter {

    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final Bucket bucket;
    private final BlockingStrategy blockingStrategy;

    public TokenBucketRateLimiter(long capacity, double refillPerSec, LongSupplier nanoClock) {
        this(capacity, refillPerSec, nanoClock, BackoffSleeper.realTime());
    }

    /** sleeper 주입용 — 테스트는 실제로 자지 않고 가짜 시계를 전진시키는 sleeper로 대기 흐름을 결정적으로 검증한다. */
    public TokenBucketRateLimiter(long capacity, double refillPerSec, LongSupplier nanoClock, BackoffSleeper sleeper) {
        validate(capacity, refillPerSec, nanoClock, sleeper);
        this.bucket = buildBucket(capacity, refillPerSec, nanoClock);
        this.blockingStrategy = toBlockingStrategy(sleeper);
    }

    private static void validate(long capacity, double refillPerSec, LongSupplier nanoClock, BackoffSleeper sleeper) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("버킷 용량은 1 이상이어야 합니다.");
        }
        if (refillPerSec <= 0) {
            throw new IllegalArgumentException("초당 보충량은 0보다 커야 합니다.");
        }
        if (nanoClock == null) {
            throw new IllegalArgumentException("시계는 비어 있을 수 없습니다.");
        }
        if (sleeper == null) {
            throw new IllegalArgumentException("sleeper는 비어 있을 수 없습니다.");
        }
    }

    private static Bucket buildBucket(long capacity, double refillPerSec, LongSupplier nanoClock) {
        return Bucket.builder()
                .addLimit(bandwidth(capacity, refillPerSec))
                .withCustomTimePrecision(nanoTimeMeter(nanoClock))
                .build();
    }

    /** 소수 refillPerSec(예: 0.5/s)를 정수 토큰/기간(1000초당 N개)으로 환산한다 — greedy는 기간에 균등 분배라 동작 동일. */
    private static Bandwidth bandwidth(long capacity, double refillPerSec) {
        long windowSeconds = 1000L;
        long refillTokens = Math.round(refillPerSec * windowSeconds);
        return Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillTokens, Duration.ofSeconds(windowSeconds))
                .build();
    }

    /** 주입받은 시계를 Bucket4j의 시계로 번역한다 — 가짜 시계일 수 있으니 벽시계 아님으로 선언. */
    private static TimeMeter nanoTimeMeter(LongSupplier nanoClock) {
        return new TimeMeter() {
            @Override
            public long currentTimeNanos() {
                return nanoClock.getAsLong();
            }

            @Override
            public boolean isWallClockBased() {
                return false;
            }
        };
    }

    /** 주입받은 sleeper(ms)를 Bucket4j의 대기 전략(ns)으로 번역한다. */
    private static BlockingStrategy toBlockingStrategy(BackoffSleeper sleeper) {
        return nanosToPark -> sleeper.sleep((long) Math.ceil(nanosToPark / NANOS_PER_MILLI));
    }

    /** 토큰이 1개 이상이면 1개 소비하고 통과(true), 없으면 거부(false). */
    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }

    /**
     * 토큰이 없으면 최대 maxWait까지 기다렸다가 소비한다. 마감 안에 찰 수 없으면 기다리지 않고
     * 즉시 거부(false)한다 — 어차피 실패할 대기로 스레드를 붙잡아 두지 않는다.
     */
    public boolean tryConsume(Duration maxWait) {
        try {
            return bucket.asBlocking().tryConsume(1, maxWait.toNanos(), blockingStrategy);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("토큰 대기 중 인터럽트되었습니다.", e);
        }
    }

    /** 토큰 1개가 찰 때까지 필요한 초를 올림(ceil)으로 반환한다(Retry-After 헤더용). 이미 1개 이상이면 0. */
    public long retryAfterSeconds() {
        EstimationProbe probe = bucket.estimateAbilityToConsume(1);
        if (probe.canBeConsumed()) {
            return 0L;
        }
        return (long) Math.ceil(probe.getNanosToWaitForRefill() / NANOS_PER_SECOND);
    }
}
