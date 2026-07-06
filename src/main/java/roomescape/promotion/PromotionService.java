package roomescape.promotion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.vo.Slot;
import roomescape.reservation.ReservationDao;
import roomescape.reservation.service.ReservationCreator;
import roomescape.waiting.WaitingDao;

/**
 * 대기 승격 오케스트레이션. 대기 신청/조회/취소(WaitingService)와 분리해, 승격이라는 관심사만 모은다.
 * 취소 흐름은 enqueuePromotion으로 할 일만 기록하고, 워커는 findPendingTasks/processTask로 실제 승격을 멱등하게 수행한다.
 */
@Service
@Transactional
public class PromotionService {
    private final PromotionOutboxDao promotionOutboxDao;
    private final WaitingDao waitingDao;
    private final ReservationDao reservationDao;
    private final ReservationCreator reservationCreator;

    public PromotionService(PromotionOutboxDao promotionOutboxDao, WaitingDao waitingDao,
                            ReservationDao reservationDao, ReservationCreator reservationCreator) {
        this.promotionOutboxDao = promotionOutboxDao;
        this.waitingDao = waitingDao;
        this.reservationDao = reservationDao;
        this.reservationCreator = reservationCreator;
    }

    /**
     * 취소 트랜잭션 안에서 호출된다. inline으로 승격하지 않고, "이 슬롯의 다음 대기자를 승격시켜라"라는 할 일만
     * 아웃박스에 기록한다(취소와 같은 트랜잭션 → 원자적). 실제 승격은 워커가 나중에 수행한다.
     */
    public void enqueuePromotion(Slot slot) {
        promotionOutboxDao.insert(PromotionTask.pending(slot));
    }

    @Transactional(readOnly = true)
    public List<PromotionTask> findPendingTasks() {
        return promotionOutboxDao.findByStatus(OutboxStatus.PENDING);
    }

    /**
     * 아웃박스 할 일 한 건을 한 트랜잭션으로 처리한다. 승격과 완료 표시(markDone)가 함께 커밋되거나 함께 롤백되며,
     * 실패하면 PENDING으로 남아 다음 주기에 멱등하게 재시도된다.
     */
    public void processTask(PromotionTask task) {
        promotePendingSlot(task.getThemeId(), task.getTimeId(), task.getDate(), task.getStoreId());
        promotionOutboxDao.markDone(task.getId());
    }

    /**
     * 여러 번 실행되어도 결과가 같도록 멱등하게 설계했다 — 두 겹:
     * 사전 검사(무잠금)는 빠른 스킵용 UX일 뿐이고, 경합의 진실은 승격 INSERT의 UNIQUE 백스톱이 가린다.
     * (빈 슬롯 FOR UPDATE는 gap 락이라 입장을 직렬화하지 못하면서 데드락만 만든다 — log_56과 동일 정리.)
     * 첫 대기자 행 락은 유지 — 실존 행이라 record 락이 성립하고, 워커끼리·승격↔대기취소를 직렬화한다.
     */
    private void promotePendingSlot(Long themeId, Long timeId, LocalDate date, Long storeId) {
        if (reservationDao.existsBySlotKey(themeId, timeId, date, storeId)) {
            return;
        }
        waitingDao.findFirstIdBySlotKeyForUpdate(themeId, timeId, date, storeId)
                .flatMap(waitingDao::findById)
                .ifPresent(first -> {
                    LocalDateTime now = LocalDateTime.now();
                    if (first.isPast(now)) {
                        return;
                    }
                    try {
                        reservationCreator.createFromPromotion(first, now);
                    } catch (DuplicateKeyException e) {
                        // 그 사이 슬롯이 참(다른 워커의 승격 또는 어드민 직접 예약).
                        // 대기는 줄을 지키고 임무만 소비한다 — 다음 취소가 새 태스크를 적재하므로 잃는 것이 없다.
                        return;
                    }
                    waitingDao.delete(first.getId());
                });
    }
}
