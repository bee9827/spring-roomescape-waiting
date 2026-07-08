package roomescape.payment.orchestration.reconciliation;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import roomescape.payment.order.OrderRetryEscalator;

/**
 * 결과 불명확(NEEDS_CHECK) 주문 자동 수렴 스케줄러. 사용자가 재시도하지 않아도, 주기적으로 깨어나
 * NEEDS_CHECK 주문을 게이트웨이에 조회해 확정/실패로 수렴시킨다.
 *
 * <p>PromotionOutboxWorker·ExpiredOrderWorker와 같은 폴링 패턴(아웃박스/최종 일관성) — 워커는 얇게
 * 폴링+위임만 하고, 수렴 로직은 PaymentReconciliationService가 소유한다. 한 건이 실패해도(토스 조회 실패 등)
 * 다음 건·다음 주기를 막지 않도록 건별로 격리한다.
 */
@Component
public class PaymentReconciliationWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationWorker.class);

    private final PaymentReconciliationService reconciliationService;
    private final OrderRetryEscalator retryEscalator;

    public PaymentReconciliationWorker(PaymentReconciliationService reconciliationService,
                                       OrderRetryEscalator retryEscalator) {
        this.reconciliationService = reconciliationService;
        this.retryEscalator = retryEscalator;
    }

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-interval-ms:60000}")
    public void reconcileUnknownPayments() {
        for (String orderId : reconciliationService.findReconcilableOrderIds()) {
            try {
                reconciliationService.reconcile(orderId);
            } catch (CallNotPermittedException e) {
                // 차단기 열림 = 환경의 실패지 이 주문의 실패가 아니다 — 개별 카운터에 계상하면 멀쩡한 주문들이
                // 장애 시간만큼 줄줄이 DEAD로 오분류된다. 나머지 건도 즉시 거절될 테니 이번 주기는 여기서 접는다.
                log.warn("토스 서킷 브레이커 열림 — 이번 reconciliation 주기 건너뜀");
                return;
            } catch (RuntimeException e) {
                // transient 가설로 다음 주기 재시도하되, 한도를 넘기면 CHECK_DEAD 격리(무한 재시도 금지).
                log.warn("결제 결과 확인(reconciliation) 실패 (다음 주기 재시도): orderId={}", orderId, e);
                retryEscalator.recordFailure(orderId);
            }
        }
    }
}
