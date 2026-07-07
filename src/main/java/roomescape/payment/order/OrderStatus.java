package roomescape.payment.order;

import java.util.Optional;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    // read timeout 등으로 승인 결과가 불명확한 상태. '실패'가 아니라 '확인 필요' —
    // reaper가 건드리지 않고, 멱등 재시도로 결과를 확정한다.
    NEEDS_CHECK,
    // 결제는 승인됐지만(돈이 나감) 예약을 확정하지 못한 상태. DB 롤백으론 못 되돌리니 환불(보상)이 필요하다 —
    // RefundWorker가 게이트웨이 취소를 호출해 FAILED로 수렴시킨다.
    NEEDS_REFUND,
    // 아래는 재시도 한도 초과로 격리된 죽은(dead letter) 상태들. transient(지나가는 실패) 가설이 기각된 것 —
    // 워커 폴링에서 빠지고 행은 보존되며(FK·이력·UNIQUE 백스톱 유지), 사람이 상태를 보고 수동 처리한다.
    CHECK_DEAD,   // NEEDS_CHECK 수렴 실패 반복: 돈이 나갔는지 불명 — 게이트웨이 대시보드와 대조 필요
    REFUND_DEAD,  // NEEDS_REFUND 수렴 실패 반복: 돈은 나갔는데 환불이 안 됨 — 수동 환불 필요
    EXPIRE_DEAD;  // PENDING 만료 정리 실패 반복 — 슬롯 점유 해제를 수동 처리 필요

    /**
     * 수렴 중(워커가 재시도하는) 상태만 죽은 짝을 가진다 — 종착 상태(CONFIRMED/FAILED)와
     * 이미 죽은 상태는 짝이 없다(빈 값). DEAD 짝의 증식이 수렴 중 상태 수에 비례해 억제되는 구조.
     */
    public Optional<OrderStatus> deadCounterpart() {
        return switch (this) {
            case NEEDS_CHECK -> Optional.of(CHECK_DEAD);
            case NEEDS_REFUND -> Optional.of(REFUND_DEAD);
            case PENDING -> Optional.of(EXPIRE_DEAD);
            default -> Optional.empty();
        };
    }
}
