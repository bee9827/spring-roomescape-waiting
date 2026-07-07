package roomescape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데드락 패자 재시도(@Retry dbLockRetry)의 두 가지 보장 검증:
 * ① 락 실패는 한도 안에서 재시도된다 ② 재시도 어스펙트가 @Transactional "바깥"에 감겨
 * 매 시도가 새 트랜잭션을 받는다(실패한 시도의 쓰기는 롤백되어 남지 않는다).
 * 어스펙트 순서가 뒤집히면(재시도가 트랜잭션 안) 실패 시도의 쓰기가 함께 커밋돼 ②가 깨진다.
 */
@SpringBootTest
@ActiveProfiles("test")
class DbLockRetryTest {

    private static final String PROBE_NAME = "재시도프로브";

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        LockFailingProbe lockFailingProbe(JdbcTemplate jdbcTemplate) {
            return new LockFailingProbe(jdbcTemplate);
        }
    }

    /** 처음 N번은 쓰기 후 데드락 패자를 흉내 내고, 그다음엔 성공하는 프로브.
     * 주입되는 건 AOP 프록시라 필드 접근은 프록시의 빈 필드를 읽는다 — 상태는 반드시 메서드로 접근한다. */
    static class LockFailingProbe {
        private final JdbcTemplate jdbcTemplate;
        private final AtomicInteger attempts = new AtomicInteger();

        LockFailingProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Retry(name = "dbLockRetry")
        @Transactional
        public void insertThenLoseDeadlock(int failuresBeforeSuccess) {
            jdbcTemplate.update("INSERT INTO stores(name) VALUES (?)", PROBE_NAME);
            if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
                throw new CannotAcquireLockException("모의 데드락 패자");
            }
        }

        @Retry(name = "dbLockRetry")
        @Transactional
        public void failWithBusinessException() {
            attempts.incrementAndGet();
            throw new IllegalStateException("락과 무관한 실패 — 재시도 대상이 아니어야 한다");
        }

        public int attemptCount() {
            return attempts.get();
        }

        public void resetAttempts() {
            attempts.set(0);
        }
    }

    @Autowired
    private LockFailingProbe probe;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        probe.resetAttempts();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM stores WHERE name = ?", PROBE_NAME);
    }

    @Test
    @DisplayName("데드락 패자는 새 트랜잭션으로 재시도되어 성공한다 — 실패한 시도의 쓰기는 롤백되어 남지 않는다")
    void retryRunsEachAttemptInFreshTransaction() {
        probe.insertThenLoseDeadlock(1); // 1회 패배 후 성공

        assertThat(probe.attemptCount()).isEqualTo(2); // 재시도 발생
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stores WHERE name = ?", Long.class, PROBE_NAME);
        // 재시도가 트랜잭션 안에 감겼다면 실패 시도의 INSERT까지 커밋돼 2가 된다 — 1이어야 어스펙트 순서가 옳다.
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("락과 무관한 예외는 재시도하지 않는다(1회로 끝) — yml retry-exceptions 필터가 살아 있음을 고정")
    void nonLockExceptionFailsFast() {
        // yml 키가 어긋나면 r4j가 조용히 기본 설정(모든 예외 재시도)으로 폴백한다 — 이 테스트가 그 드리프트를 잡는다.
        assertThatThrownBy(() -> probe.failWithBusinessException())
                .isInstanceOf(IllegalStateException.class);

        assertThat(probe.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("한도(3회)를 다 써도 지면 예외가 전파되고, 모든 시도의 쓰기가 롤백된다")
    void exhaustedRetryPropagatesAndLeavesNothing() {
        assertThatThrownBy(() -> probe.insertThenLoseDeadlock(Integer.MAX_VALUE))
                .isInstanceOf(CannotAcquireLockException.class);

        assertThat(probe.attemptCount()).isEqualTo(3); // max-attempts
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stores WHERE name = ?", Long.class, PROBE_NAME);
        assertThat(rows).isZero();
    }
}
