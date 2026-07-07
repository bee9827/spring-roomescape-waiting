package roomescape.payment.order.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;
import roomescape.payment.order.Order;
import roomescape.payment.order.OrderDao;
import roomescape.payment.order.OrderStatus;

@Repository
public class OrderJdbcDao implements OrderDao {
    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNum) -> Order.reconstruct(
            rs.getLong("id"),
            rs.getString("order_id"),
            rs.getString("idempotency_key"),
            rs.getLong("reservation_id"),
            rs.getLong("amount"),
            rs.getString("payment_key"),
            OrderStatus.valueOf(rs.getString("status"))
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SimpleJdbcInsert simpleJdbcInsert;

    public OrderJdbcDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.simpleJdbcInsert = new SimpleJdbcInsert(jdbcTemplate.getJdbcTemplate())
                .withTableName("orders")
                .usingGeneratedKeyColumns("id")
                .usingColumns("order_id", "idempotency_key", "reservation_id", "amount", "status");
    }

    @Override
    public Order insert(Order order) {
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("order_id", order.getOrderId())
                .addValue("idempotency_key", order.getIdempotencyKey())
                .addValue("reservation_id", order.getReservationId())
                .addValue("amount", order.getAmount())
                .addValue("status", order.getStatus().name());
        Long id = simpleJdbcInsert.executeAndReturnKey(params).longValue();
        return Order.reconstruct(id, order.getOrderId(), order.getIdempotencyKey(), order.getReservationId(),
                order.getAmount(), order.getPaymentKey(), order.getStatus());
    }

    @Override
    public Optional<Order> findByOrderId(String orderId) {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE order_id = :orderId
                """;
        return jdbcTemplate.query(sql, new MapSqlParameterSource("orderId", orderId), ROW_MAPPER)
                .stream().findFirst();
    }

    @Override
    public Optional<Order> findPendingByReservationId(Long reservationId) {
        return findByReservationIdAndStatus(reservationId, OrderStatus.PENDING);
    }

    @Override
    public Optional<Order> findConfirmedByReservationId(Long reservationId) {
        return findByReservationIdAndStatus(reservationId, OrderStatus.CONFIRMED);
    }

    private Optional<Order> findByReservationIdAndStatus(Long reservationId, OrderStatus status) {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE reservation_id = :reservationId AND status = :status
                """;
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("reservationId", reservationId)
                .addValue("status", status.name());
        return jdbcTemplate.query(sql, params, ROW_MAPPER).stream().findFirst();
    }

    @Override
    public Order update(Order order) {
        // 상태가 "바뀔 때만" 워커 실패 카운터를 리셋한다 — 같은 상태 재기록(recheck의 NEEDS_CHECK→NEEDS_CHECK)이
        // 카운터를 밀면, 사용자가 재확인을 반복하는 것만으로 재시도 바운드를 영원히 우회할 수 있다.
        String sql = """
                UPDATE orders
                SET payment_key = :paymentKey, status = :status,
                    attempt_count = CASE WHEN status = :status THEN attempt_count ELSE 0 END
                WHERE order_id = :orderId
                """;
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("paymentKey", order.getPaymentKey())
                .addValue("status", order.getStatus().name())
                .addValue("orderId", order.getOrderId());
        jdbcTemplate.update(sql, params);
        return order;
    }

    @Override
    public List<Order> findExpiredPending(LocalDateTime threshold) {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE status = :status AND created_at < :threshold
                """;
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", OrderStatus.PENDING.name())
                .addValue("threshold", threshold);
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    @Override
    public List<Order> findByReservationIds(List<Long> reservationIds) {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE reservation_id IN (:reservationIds)
                """;
        SqlParameterSource params = new MapSqlParameterSource("reservationIds", reservationIds);
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    @Override
    public int compareAndUpdate(Order order, OrderStatus expectedStatus) {
        // 상태가 바뀌면 워커 실패 카운터는 0부터 다시 — 카운터는 "현재 상태에서의 실패 수"만 센다.
        String sql = """
                UPDATE orders
                SET payment_key = :paymentKey, status = :status, attempt_count = 0
                WHERE order_id = :orderId AND status = :expected
                """;
        SqlParameterSource params = new MapSqlParameterSource()
                .addValue("paymentKey", order.getPaymentKey())
                .addValue("status", order.getStatus().name())
                .addValue("orderId", order.getOrderId())
                .addValue("expected", expectedStatus.name());
        return jdbcTemplate.update(sql, params);
    }

    @Override
    public int incrementAndGetAttempt(String orderId, OrderStatus expectedStatus) {
        // 상태 가드: 증가 직전에 상태가 전이됐다면 이 실패는 낡은 정보 — 새 상태에 유령 실패를 계상하지 않는다.
        String update = """
                UPDATE orders
                SET attempt_count = attempt_count + 1
                WHERE order_id = :orderId AND status = :expected
                """;
        SqlParameterSource params = new MapSqlParameterSource("orderId", orderId)
                .addValue("expected", expectedStatus.name());
        if (jdbcTemplate.update(update, params) == 0) {
            return 0; // 상태가 이미 바뀜 — 셀 것이 없다(0은 한도에 절대 닿지 않는 값)
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM orders WHERE order_id = :orderId", params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public List<Order> findNeedsCheck() {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE status = :status
                """;
        SqlParameterSource params = new MapSqlParameterSource("status", OrderStatus.NEEDS_CHECK.name());
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }

    @Override
    public List<Order> findNeedsRefund() {
        String sql = """
                SELECT id, order_id, idempotency_key, reservation_id, amount, payment_key, status
                FROM orders
                WHERE status = :status
                """;
        SqlParameterSource params = new MapSqlParameterSource("status", OrderStatus.NEEDS_REFUND.name());
        return jdbcTemplate.query(sql, params, ROW_MAPPER);
    }
}
