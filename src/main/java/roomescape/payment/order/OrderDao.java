package roomescape.payment.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OrderDao {
    Order insert(Order order);

    Optional<Order> findByOrderId(String orderId);

    Optional<Order> findPendingByReservationId(Long reservationId);

    Optional<Order> findConfirmedByReservationId(Long reservationId);

    Order update(Order order);

    /**
     * 상태 전이를 compare-and-set으로 수행한다 — DB의 현재 status가 expectedStatus일 때만 바꾼다.
     * 동시에 같은 주문을 수렴시키려는 둘 중 한쪽만 1행을 바꾸고(이김), 나머지는 0행(이미 바뀜 → 짐).
     * 반환값(바뀐 행 수)으로 "내가 이겼는지"를 안다 — status 자체가 낙관 락의 version 역할.
     */
    int compareAndUpdate(Order order, OrderStatus expectedStatus);

    /**
     * 기대 상태일 때만 워커 실패 횟수를 1 올리고 올린 값을 돌려준다(상태가 이미 바뀌었으면 0 — 낡은 실패는 안 센다).
     * 카운터는 상태 전이(update/compareAndUpdate) 때 0으로 리셋된다 — "현재 상태에서 몇 번 실패했나"만 센다.
     */
    int incrementAndGetAttempt(String orderId, OrderStatus expectedStatus);

    List<Order> findExpiredPending(LocalDateTime threshold);

    List<Order> findByReservationIds(List<Long> reservationIds);

    List<Order> findNeedsCheck();

    List<Order> findNeedsRefund();
}
