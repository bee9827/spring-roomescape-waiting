package roomescape.payment.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 워커 재시도 한도 관리 — 메시징 시스템의 DLQ(dead letter queue)를 상태 기계로 번역한 것.
 * transient(지나가는 실패) 분류는 가설이다: 한도만큼 연속 실패하면 가설이 기각된 것으로 보고
 * permanent로 재분류해 DEAD 상태로 격리한다(자동 재시도 중단 + 행 보존 + 사람 개입 대기).
 * 실패 기록은 워커의 catch 블록에서 호출된다 — 실패한 처리 트랜잭션은 이미 롤백됐고, 이 기록은 새 트랜잭션.
 */
@Service
public class OrderRetryEscalator {

    private static final Logger log = LoggerFactory.getLogger(OrderRetryEscalator.class);

    private final OrderService orderService;
    private final int maxAttempts;

    public OrderRetryEscalator(OrderService orderService,
                               @Value("${worker.retry.max-attempts:5}") int maxAttempts) {
        this.orderService = orderService;
        this.maxAttempts = maxAttempts;
    }

    /**
     * 워커의 주문 처리 실패 1회를 기록하고, 한도를 넘기면 DEAD로 격리한다.
     * 알림 채널이 없는 현재의 "사람 호출"은 ERROR 로그다 — 격리는 조용히 일어나면 안 된다.
     * 기록 자체의 실패는 여기서 격리한다(원인이 DB 장애면 기록도 던질 확률이 높다) —
     * 워커 배치의 나머지 건 처리를 막지 않고, 못 센 실패는 다음 주기에 다시 세면 된다.
     */
    @Transactional
    public void recordFailure(String orderId) {
        try {
            orderService.findByOrderId(orderId).ifPresent(order -> {
                if (order.getStatus().deadCounterpart().isEmpty()) {
                    return; // 그 사이 수렴됐거나 이미 격리됨 — 셀 것이 없다
                }
                int attempts = orderService.recordFailedAttempt(order.getOrderId(), order.getStatus());
                if (attempts >= maxAttempts && orderService.escalateToDead(order)) {
                    log.error("워커 재시도 한도({}) 초과 — 주문을 {}로 격리. 사람 개입 필요: orderId={}",
                            maxAttempts, order.getStatus(), orderId);
                }
            });
        } catch (RuntimeException e) {
            log.warn("워커 실패 기록 자체가 실패 — 다음 주기에 다시 센다: orderId={}", orderId, e);
        }
    }
}
