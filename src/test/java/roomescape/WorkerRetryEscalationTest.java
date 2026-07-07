package roomescape;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import roomescape.common.exception.BusinessRuleViolationException;
import roomescape.common.vo.Name;
import roomescape.common.vo.Slot;
import roomescape.member.Member;
import roomescape.member.MemberDao;
import roomescape.payment.order.Order;
import roomescape.payment.order.OrderDao;
import roomescape.payment.order.OrderRetryEscalator;
import roomescape.payment.order.OrderService;
import roomescape.payment.order.OrderStatus;
import roomescape.payment.orchestration.reconciliation.PaymentReconciliationService;
import roomescape.payment.orchestration.refund.PaymentRefundService;
import roomescape.promotion.PromotionService;
import roomescape.promotion.PromotionTask;
import roomescape.reservation.Reservation;
import roomescape.reservation.ReservationDao;
import roomescape.store.Store;
import roomescape.theme.Theme;
import roomescape.theme.ThemeDao;
import roomescape.time.Time;
import roomescape.time.TimeDao;

/**
 * 워커 재시도 한도(DLQ의 상태 기계 번역) 검증 — 한도 초과 시 DEAD 격리(폴링 이탈 + 행 보존),
 * 상태 전이 시 카운터 리셋, DEAD 주문의 재결제 차단(UNIQUE 백스톱 유지).
 */
@SpringBootTest(properties = "worker.retry.max-attempts=2")
@ActiveProfiles("test")
class WorkerRetryEscalationTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderDao orderDao;
    @Autowired
    private OrderRetryEscalator retryEscalator;
    @Autowired
    private PaymentReconciliationService reconciliationService;
    @Autowired
    private PaymentRefundService refundService;
    @Autowired
    private PromotionService promotionService;
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

    private Member member;
    private Time time;
    private Theme theme;
    private Store store;
    private Reservation reservation;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("INSERT INTO stores(name) VALUES (?)", "강남점");
        Long storeId = jdbcTemplate.queryForObject("SELECT id FROM stores WHERE name = ?", Long.class, "강남점");
        store = new Store(storeId, "강남점");
        jdbcTemplate.update(
                "INSERT INTO members(name, email, password, role) VALUES (?, ?, ?, ?)",
                "유저", "user@test.com", "password", "USER"
        );
        member = memberDao.findByEmail("user@test.com").orElseThrow();
        time = timeDao.insert(new Time(LocalTime.of(13, 0)));
        theme = themeDao.insert(new Theme(new Name("방탈출"), "http://url", "설명"));
        reservation = reservationDao.insert(
                Reservation.createByAdmin(member, LocalDate.now().plusDays(1), time, theme, store));
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
    @DisplayName("NEEDS_CHECK 수렴 실패가 한도에 닿으면 CHECK_DEAD로 격리되고 reconciliation 폴링에서 빠진다")
    void reconcileFailuresEscalateToCheckDead() {
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");

        retryEscalator.recordFailure(order.getOrderId());
        assertThat(reconciliationService.findReconcilableOrderIds()).contains(order.getOrderId()); // 아직 재시도 대상

        retryEscalator.recordFailure(order.getOrderId());

        assertThat(orderDao.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CHECK_DEAD);
        assertThat(reconciliationService.findReconcilableOrderIds()).isEmpty(); // 폴링 이탈
    }

    @Test
    @DisplayName("NEEDS_REFUND 수렴 실패가 한도에 닿으면 REFUND_DEAD로 격리되고 환불 폴링에서 빠진다")
    void refundFailuresEscalateToRefundDead() {
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");
        Order fresh = orderService.findByOrderId(order.getOrderId()).orElseThrow();
        orderService.markNeedsRefund(fresh);

        retryEscalator.recordFailure(order.getOrderId());
        retryEscalator.recordFailure(order.getOrderId());

        assertThat(orderDao.findByOrderId(order.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.REFUND_DEAD);
        assertThat(refundService.findRefundableOrderIds()).isEmpty();
    }

    @Test
    @DisplayName("상태 전이(수렴 성공)는 실패 카운터를 리셋한다 — 카운터는 '현재 상태에서의 실패 수'만 센다")
    void successfulTransitionResetsAttemptCount() {
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");
        retryEscalator.recordFailure(order.getOrderId());
        assertThat(attemptCountOf(order)).isEqualTo(1);

        Order fresh = orderService.findByOrderId(order.getOrderId()).orElseThrow();
        assertThat(orderService.complete(fresh, "pk-1")).isTrue(); // NEEDS_CHECK → CONFIRMED 수렴 성공

        assertThat(attemptCountOf(order)).isZero();
    }

    @Test
    @DisplayName("같은 상태 재기록(recheck 결과 여전히 불명)은 카운터를 밀지 않는다 — 재시도 바운드 우회 차단")
    void sameStatusRewriteKeepsAttemptCount() {
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");
        retryEscalator.recordFailure(order.getOrderId());
        assertThat(attemptCountOf(order)).isEqualTo(1);

        Order fresh = orderService.findByOrderId(order.getOrderId()).orElseThrow();
        orderService.markNeedsCheck(fresh, "pk-1"); // 사용자 재확인, 결과 여전히 불명 — NEEDS_CHECK → NEEDS_CHECK

        assertThat(attemptCountOf(order)).isEqualTo(1); // 리셋되면 사용자가 recheck 반복만으로 바운드를 우회한다
    }

    @Test
    @DisplayName("승격 태스크 실패가 한도에 닿으면 DEAD로 격리되고 워커 폴링에서 빠진다")
    void promotionFailuresEscalateToDead() {
        promotionService.enqueuePromotion(
                new Slot(LocalDate.now().plusDays(2), time, theme, store));
        PromotionTask task = promotionService.findPendingTasks().get(0);

        promotionService.recordFailure(task);
        assertThat(promotionService.findPendingTasks()).hasSize(1); // 아직 재시도 대상

        promotionService.recordFailure(task);

        assertThat(promotionService.findPendingTasks()).isEmpty(); // 폴링 이탈
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM promotion_outbox WHERE id = ?", String.class, task.getId());
        assertThat(status).isEqualTo("DEAD");
    }

    @Test
    @DisplayName("DEAD로 격리된 주문의 예약은 새 결제를 시작할 수 없다 — 사람이 정리하기 전까지 UNIQUE 백스톱이 막는다")
    void deadOrderBlocksNewPayment() {
        Order order = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(order, "pk-1");
        retryEscalator.recordFailure(order.getOrderId());
        retryEscalator.recordFailure(order.getOrderId()); // → CHECK_DEAD

        assertThatThrownBy(() -> orderService.getOrCreate(reservation.getId(), 10_000L))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private int attemptCountOf(Order order) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM orders WHERE order_id = ?", Integer.class, order.getOrderId());
        return count != null ? count : -1;
    }
}
