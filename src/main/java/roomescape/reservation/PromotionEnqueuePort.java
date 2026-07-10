package roomescape.reservation;

import roomescape.common.vo.Slot;

/**
 * 예약 취소·변경 시 "이 슬롯의 다음 대기자 승격을 예약(enqueue)하라"를 요청하기 위한 포트.
 * reservation이 필요로 하는 것을 reservation 쪽에서 선언한다(DIP) — 구현은 promotion이 제공한다.
 * outbox는 시간적 결합만 끊으므로, 구조적(import) 사이클은 이 포트로 끊는다(reservation은 promotion을 import하지 않는다).
 */
public interface PromotionEnqueuePort {

    void enqueuePromotion(Slot slot);
}
