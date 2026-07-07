package roomescape.promotion;

public enum OutboxStatus {
    PENDING,
    DONE,
    // 재시도 한도 초과로 격리된 죽은(dead letter) 태스크. 폴링에서 빠지고 행은 보존 — 사람 개입 필요.
    // 주의: 승격 태스크가 죽으면 새 태스크는 "취소"가 있어야만 적재되므로 그 슬롯은 림보가 될 수 있다.
    DEAD
}
