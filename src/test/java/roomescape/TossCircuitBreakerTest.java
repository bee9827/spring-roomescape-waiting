package roomescape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import roomescape.common.vo.Name;
import roomescape.member.Member;
import roomescape.member.MemberDao;
import roomescape.payment.exception.PaymentResultUnknownException;
import roomescape.payment.order.Order;
import roomescape.payment.order.OrderService;
import roomescape.payment.order.OrderStatus;
import roomescape.payment.orchestration.reconciliation.PaymentReconciliationWorker;
import roomescape.payment.toss.TossPaymentException;
import roomescape.reservation.Reservation;
import roomescape.reservation.ReservationDao;
import roomescape.store.Store;
import roomescape.theme.Theme;
import roomescape.theme.ThemeDao;
import roomescape.time.Time;
import roomescape.time.TimeDao;

/**
 * 토스 서킷 브레이커 검증 — 시간 대기 없이 레지스트리로 상태를 제어해 결정적으로:
 * 트립 = 실패율 AND 최소 표본 / 4xx는 안 셈(건강한 토스의 정상 답변) / half-open 정찰 성공 시 복귀 /
 * 열려 있는 동안 워커는 개별 주문 카운터에 계상하지 않는다(환경의 실패 ≠ 주문의 실패).
 */
@SpringBootTest(properties = {
        "resilience4j.circuitbreaker.instances.toss.sliding-window-size=4",
        "resilience4j.circuitbreaker.instances.toss.minimum-number-of-calls=2",
        "resilience4j.circuitbreaker.instances.toss.failure-rate-threshold=50",
        "resilience4j.circuitbreaker.instances.toss.wait-duration-in-open-state=100s",
        "resilience4j.circuitbreaker.instances.toss.permitted-number-of-calls-in-half-open-state=2"
})
@ActiveProfiles("test")
class TossCircuitBreakerTest {

    @TestConfiguration
    static class ProbeConfig {
        @Bean
        TossProbe tossProbe() {
            return new TossProbe();
        }
    }

    /** 지정한 예외를 던지거나 성공하는 프로브 — 인스턴스 "toss"의 창에 계상된다. */
    static class TossProbe {
        @CircuitBreaker(name = "toss")
        public void call(RuntimeException failure) {
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Autowired
    private TossProbe probe;
    @Autowired
    private CircuitBreakerRegistry registry;
    @Autowired
    private PaymentReconciliationWorker reconciliationWorker;
    @Autowired
    private OrderService orderService;
    @Autowired
    private ReservationDao reservationDao;
    @Autowired
    private MemberDao memberDao;
    @Autowired
    private TimeDao timeDao;
    @Autowired
    private ThemeDao themeDao;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetBreaker() {
        registry.circuitBreaker("toss").reset();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM promotion_outbox");
        jdbcTemplate.update("DELETE FROM waitings");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM times");
        jdbcTemplate.update("DELETE FROM themes");
        jdbcTemplate.update("DELETE FROM members");
        jdbcTemplate.update("DELETE FROM stores");
    }

    @Test
    @DisplayName("아픈 신호(read timeout)가 최소 표본을 채우고 임계를 넘으면 회로가 열리고, 이후 호출은 즉시 거절된다")
    void tripsOnUnhealthySignals() {
        failOnce(new PaymentResultUnknownException("read timeout", null));
        failOnce(new PaymentResultUnknownException("read timeout", null)); // 표본 2, 실패율 100%

        assertThat(registry.circuitBreaker("toss").getState()).isEqualTo(State.OPEN);
        assertThatThrownBy(() -> probe.call(null)) // 성공했을 호출조차
                .isInstanceOf(CallNotPermittedException.class); // 실행 없이 즉시 거절
    }

    @Test
    @DisplayName("최소 표본 미만이면 실패율 100%여도 열리지 않는다 — 아침 첫 호출 1건 실패로 차단하지 않는다")
    void doesNotTripBelowMinimumCalls() {
        failOnce(new PaymentResultUnknownException("read timeout", null)); // 표본 1

        assertThat(registry.circuitBreaker("toss").getState()).isEqualTo(State.CLOSED);
        probe.call(null); // 여전히 실행된다(즉시 거절 아님)
    }

    @Test
    @DisplayName("4xx(카드 거절 등)는 실패로 세지 않는다 — 사용자가 여러 번 실패해도 회로는 닫혀 있다")
    void ignoresClientErrors() {
        for (int i = 0; i < 4; i++) { // 창(4)을 가득 채워도
            failOnce(new TossPaymentException.CardRejected("한도 초과"));
        }

        assertThat(registry.circuitBreaker("toss").getState()).isEqualTo(State.CLOSED);
        probe.call(null);
    }

    @Test
    @DisplayName("half-open 정찰 호출들이 성공하면 닫힘(closed)으로 복귀한다")
    void recoversViaHalfOpen() {
        registry.circuitBreaker("toss").transitionToOpenState();
        registry.circuitBreaker("toss").transitionToHalfOpenState(); // wait-duration 경과를 흉내

        probe.call(null); // 정찰 1
        probe.call(null); // 정찰 2 — permitted(2)를 전부 성공으로 채우면 복귀 판정

        assertThat(registry.circuitBreaker("toss").getState()).isEqualTo(State.CLOSED);
    }

    @Test
    @DisplayName("회로가 열려 있는 동안 워커는 주문 카운터에 계상하지 않는다 — 환경의 실패는 주문의 실패가 아니다")
    void openBreakerDoesNotBurnOrderRetryBudget() {
        jdbcTemplate.update("INSERT INTO stores(name) VALUES (?)", "강남점");
        Long storeId = jdbcTemplate.queryForObject("SELECT id FROM stores WHERE name = ?", Long.class, "강남점");
        jdbcTemplate.update("INSERT INTO members(name, email, password, role) VALUES (?, ?, ?, ?)",
                "유저", "user@test.com", "password", "USER");
        Member member = memberDao.findByEmail("user@test.com").orElseThrow();
        Time time = timeDao.insert(new Time(LocalTime.of(13, 0)));
        Theme theme = themeDao.insert(new Theme(new Name("방탈출"), "http://url", "설명"));
        Reservation reservation = reservationDao.insert(
                Reservation.createByAdmin(member, LocalDate.now().plusDays(1), time, theme, new Store(storeId, "강남점")));
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");

        registry.circuitBreaker("toss").transitionToOpenState(); // 토스 장애 감지 상태

        reconciliationWorker.reconcileUnknownPayments(); // 워커가 여러 주기 돌아도
        reconciliationWorker.reconcileUnknownPayments();
        reconciliationWorker.reconcileUnknownPayments();

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM orders WHERE order_id = ?", Integer.class, order.getOrderId());
        assertThat(attempts).isZero(); // 계상 0 — DEAD로 오분류될 씨앗이 없다
        assertThat(orderService.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.NEEDS_CHECK); // 토스가 살아나면 그대로 수렴 대상
    }

    private void failOnce(RuntimeException failure) {
        assertThatThrownBy(() -> probe.call(failure)).isInstanceOf(failure.getClass());
    }
}
