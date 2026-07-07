package roomescape.promotion;

import java.util.List;

public interface PromotionOutboxDao {

    PromotionTask insert(PromotionTask task);

    List<PromotionTask> findByStatus(OutboxStatus status);

    void markDone(Long id);

    /** 워커 실패 횟수를 1 올리고 올린 값을 돌려준다(한도 판정은 호출자 몫). */
    int incrementAndGetAttempt(Long id);

    /** PENDING일 때만 DEAD로 격리한다(상태 CAS) — 동시 markDone과 경합해도 한쪽만 이긴다. */
    boolean markDead(Long id);
}
