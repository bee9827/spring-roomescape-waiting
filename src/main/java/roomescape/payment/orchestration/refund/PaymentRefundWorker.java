package roomescape.payment.orchestration.refund;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import roomescape.payment.order.OrderRetryEscalator;

/**
 * 환불(보상) 자동 처리 스케줄러. 결제는 됐지만 예약 확정에 실패한(NEEDS_REFUND) 주문을 주기적으로 주워
 * 게이트웨이 취소로 환불한다. PaymentReconciliationWorker와 같은 폴링 패턴(아웃박스/최종 일관성) —
 * 워커는 얇게 폴링+위임만 하고, 보상 로직은 PaymentRefundService가 소유한다. 한 건이 실패해도(취소 불명확 등)
 * 다음 건·다음 주기를 막지 않도록 건별로 격리한다(멱등키라 재시도가 안전).
 */
@Component
public class PaymentRefundWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentRefundWorker.class);

    private final PaymentRefundService refundService;
    private final OrderRetryEscalator retryEscalator;

    public PaymentRefundWorker(PaymentRefundService refundService, OrderRetryEscalator retryEscalator) {
        this.refundService = refundService;
        this.retryEscalator = retryEscalator;
    }

    @Scheduled(fixedDelayString = "${payment.refund.poll-interval-ms:60000}")
    public void refundUnsecuredPayments() {
        for (String orderId : refundService.findRefundableOrderIds()) {
            try {
                refundService.refund(orderId);
            } catch (CallNotPermittedException e) {
                // 차단기 열림 = 환경의 실패지 이 주문의 실패가 아니다 — 계상하면 환불 대기 주문들이
                // 장애 시간만큼 REFUND_DEAD로 오분류된다(돈 걸린 오탐). 이번 주기는 여기서 접는다.
                log.warn("토스 서킷 브레이커 열림 — 이번 환불 주기 건너뜀");
                return;
            } catch (RuntimeException e) {
                // transient 가설로 다음 주기 재시도하되, 한도를 넘기면 REFUND_DEAD 격리 — 돈이 걸린 격리라
                // 조용히 죽으면 안 된다(격리 시 ERROR 로그로 사람 호출).
                log.warn("환불(보상) 처리 실패 (다음 주기 재시도): orderId={}", orderId, e);
                retryEscalator.recordFailure(orderId);
            }
        }
    }
}
