package roomescape.payment.toss;

import java.util.function.Predicate;
import roomescape.payment.exception.PaymentGatewayUnreachableException;
import roomescape.payment.exception.PaymentResultUnknownException;

/**
 * 서킷 브레이커가 "토스가 아프다"로 세야 할 예외의 판정자. 차단기는 건강을 재는 장치라
 * 아픈 신호만 세야 한다 — 4xx(카드 거절·잘못된 요청 등)는 건강한 토스의 정상 답변이므로 세면
 * 사용자가 잘못 여러 번 시도하는 것만으로 차단기가 열리는 오작동이 생긴다.
 */
public class TossHealthFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        if (t instanceof PaymentResultUnknownException || t instanceof PaymentGatewayUnreachableException) {
            return true; // read timeout·연결 실패 — 토스에 닿지 못하거나 답을 못 받음
        }
        if (t instanceof TossPaymentException e) {
            return e.getStatus().is5xxServerError(); // 토스 내부 오류만 아픔. 4xx는 정상 응답의 거절
        }
        return false;
    }
}
