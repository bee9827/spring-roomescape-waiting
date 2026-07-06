package roomescape;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import roomescape.common.exception.DuplicateEntityException;
import roomescape.common.vo.Name;
import roomescape.member.Member;
import roomescape.member.MemberDao;
import roomescape.payment.order.Order;
import roomescape.payment.order.OrderDao;
import roomescape.payment.order.OrderService;
import roomescape.payment.order.OrderStatus;
import roomescape.reservation.Reservation;
import roomescape.reservation.ReservationDao;
import roomescape.reservation.service.ReservationService;
import roomescape.reservation.web.dto.ReservationRequestDto;
import roomescape.store.Store;
import roomescape.theme.Theme;
import roomescape.theme.ThemeDao;
import roomescape.time.Time;
import roomescape.time.TimeDao;

/**
 * 진짜 경합 증명 — H2 단일 트랜잭션으론 메커니즘만 증명됐던 것을 MySQL + 동시 스레드로 닫는다.
 * (H2는 gap lock 미지원 → ReservationConcurrencyTest는 successCount >= 1로 물러서 있음.
 * 여기서는 == 1로 못 박는다.)
 */
@SpringBootTest(properties = "spring.sql.init.mode=always")
@ActiveProfiles("test")
@Testcontainers
class MySqlConcurrencyProofTest {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private ReservationDao reservationDao;
    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderDao orderDao;
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
    @DisplayName("MySQL 진짜 경합: 같은 슬롯에 3개 동시 예약 요청이 들어오면 정확히 하나만 성공한다")
    void concurrentInsertExactlyOneWins() throws Exception {
        int threadCount = 3;
        ReservationRequestDto request = new ReservationRequestDto(
                LocalDate.now().plusDays(2),
                time.getId(),
                theme.getId(),
                store.getId()
        );

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    reservationService.create(member, request);
                    successCount.incrementAndGet();
                } catch (DuplicateEntityException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertThat(doneLatch.await(60, TimeUnit.SECONDS)).as("스레드가 60초 내에 끝나야 한다(락 대기 교착 의심)").isTrue();

        // 진단: 패자가 409가 아닌 다른 예외로 죽으면 InnoDB의 데드락 기록과 함께 보여준다
        if (!unexpected.isEmpty()) {
            System.out.println("=== unexpected exceptions ===");
            unexpected.forEach(System.out::println);
            System.out.println("=== SHOW ENGINE INNODB STATUS ===");
            // PROCESS 권한이 필요해 애플리케이션 유저 대신 root로 조회
            try (var conn = java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                 var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SHOW ENGINE INNODB STATUS")) {
                if (rs.next()) {
                    System.out.println(rs.getString("Status"));
                }
            }
        }

        // H2에서는 >= 1로 물러섰던 단언 — MySQL(gap lock)에서는 정확히 1을 보장해야 한다.
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
    }

    @Test
    @DisplayName("MySQL 진짜 경합: 같은 NEEDS_CHECK 주문을 10개 스레드가 동시에 확정하면 정확히 하나만 이긴다")
    void concurrentCompleteExactlyOneWins() throws InterruptedException {
        Reservation reservation = reservationDao.insert(
                Reservation.createByAdmin(member, LocalDate.now().plusDays(1), time, theme, store));
        Order created = orderService.create(reservation.getId(), 10_000L);
        orderService.markNeedsCheck(created, "pk-race");

        // 전원이 전이 전(NEEDS_CHECK) 스냅샷을 들고 출발 — 낡은 정보로 동시에 CAS를 쏘는 상황
        int threadCount = 10;
        List<Order> snapshots = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            snapshots.add(orderService.findByOrderId(created.getOrderId()).orElseThrow());
        }

        AtomicInteger winCount = new AtomicInteger(0);
        AtomicInteger loseCount = new AtomicInteger(0);
        List<String> unexpected = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (Order snapshot : snapshots) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    if (orderService.complete(snapshot, "pk-race")) {
                        winCount.incrementAndGet();
                    } else {
                        loseCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    unexpected.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown();
        assertThat(doneLatch.await(60, TimeUnit.SECONDS)).as("스레드가 60초 내에 끝나야 한다(락 대기 교착 의심)").isTrue();

        assertThat(unexpected).isEmpty();
        assertThat(winCount.get()).isEqualTo(1);
        assertThat(loseCount.get()).isEqualTo(threadCount - 1);
        assertThat(orderDao.findByOrderId(created.getOrderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }
}
