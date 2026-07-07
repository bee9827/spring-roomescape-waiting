package roomescape.payment.order.abandon;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import roomescape.payment.order.OrderRetryEscalator;
import roomescape.reservation.service.ReservationService;

/**
 * 결제 미완료(abandonment) 정리 스케줄러. 손님이 결제창을 닫아 success/fail 신호가 안 와도,
 * 주기적으로 깨어나 created_at이 TTL을 넘긴 PENDING 주문을 찾아 정리(취소)한다.
 *
 * <p>PromotionOutboxWorker와 같은 폴링 패턴: 워커는 얇게 폴링+위임만 하고, 정리 로직은
 * OrderAbandonmentService가 소유한다. 한 건 처리가 실패해도 다음 건을 막지 않도록 건별로 격리한다.
 *
 * <p>스케줄러 주기(얼마나 자주 보나)와 TTL(얼마나 오래된 걸 정리하나)은 별개다 — created_at으로
 * 나이를 보므로, 주기와 무관하게 갓 만든 PENDING(결제 진행 중)은 절대 건드리지 않는다.
 */
@Component
public class PaymentAbandonmentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentAbandonmentWorker.class);

    private final PaymentAbandonmentService abandonmentService;
    private final ReservationService reservationService;
    private final OrderRetryEscalator retryEscalator;
    private final long ttlMinutes;

    public PaymentAbandonmentWorker(PaymentAbandonmentService abandonmentService, ReservationService reservationService,
                                    OrderRetryEscalator retryEscalator,
                                    @Value("${payment.expiry.ttl-minutes:30}") long ttlMinutes) {
        this.abandonmentService = abandonmentService;
        this.reservationService = reservationService;
        this.retryEscalator = retryEscalator;
        this.ttlMinutes = ttlMinutes;
    }

    @Scheduled(fixedDelayString = "${payment.expiry.poll-interval-ms:60000}")
    public void expireAbandonedOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ttlMinutes);
        for (String orderId : abandonmentService.findExpiredPendingOrderIds(threshold)) {
            try {
                abandonmentService.expire(orderId);
            } catch (RuntimeException e) {
                // transient 가설로 다음 주기 재시도하되, 한도를 넘기면 EXPIRE_DEAD 격리(무한 재시도 금지).
                log.warn("결제 만료 정리 실패 (다음 주기 재시도): orderId={}", orderId, e);
                retryEscalator.recordFailure(orderId);
            }
        }
        for (Long reservationId : reservationService.findExpiredOrphanPendingIds(threshold)) {
            try {
                reservationService.cancelPending(reservationId);
            } catch (RuntimeException e) {
                // 주문이 없는(승격 유래) PENDING이라 실패 카운터를 둘 곳이 없다 — DB-로컬 실패뿐이라
                // transient 가설을 유지하고 다음 주기에 맡긴다(재시도 바운드는 주문 기반 경로만).
                log.warn("승격 PENDING 만료 정리 실패 (다음 주기 재시도): reservationId={}", reservationId, e);
            }
        }
    }
}
