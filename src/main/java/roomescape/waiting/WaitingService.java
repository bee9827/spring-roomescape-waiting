package roomescape.waiting;

import io.github.resilience4j.retry.annotation.Retry;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.exception.BusinessRuleViolationException;
import roomescape.common.exception.DuplicateEntityException;
import roomescape.common.exception.EntityNotFoundException;
import roomescape.member.Member;
import roomescape.reservation.Reservation;
import roomescape.reservation.ReservationDao;
import roomescape.waiting.web.dto.WaitingRequestDto;

@Service
@Transactional
public class WaitingService {
    private final WaitingDao waitingDao;
    private final ReservationDao reservationDao;

    public WaitingService(WaitingDao waitingDao, ReservationDao reservationDao) {
        this.waitingDao = waitingDao;
        this.reservationDao = reservationDao;
    }

    // 데드락 패자 재시도: 앵커 행 락·waitings 삽입이 얽히는 좁은 창의 패자를 즉시 재시도로 회수(transient).
    @Retry(name = "dbLockRetry")
    public Waiting create(WaitingRequestDto waitingRequestDto, Member member) {
        // 전제조건: "대기 최대 5명"은 카운트 제약이라 UNIQUE로 못 내린다 — 대기 수를 늘리는 모든 경로가
        // 아래 예약 행 락(앵커)을 먼저 잡아 직렬화된다는 가정 위에서만 성립한다(잠글 실존 행이 있어 record 락 성립).
        // 앵커를 우회해 큐에 직접 INSERT하는 경로가 생기면 보장이 깨진다. 잠금은 id만(JOIN 금지, log_56).
        // 주의: 이 트랜잭션의 읽기 스냅샷(read view)은 앵커 락 "이후" 첫 일반 SELECT(findById)에서 생성된다.
        // 그래서 큐 읽기가 선행 앵커 보유자들의 커밋을 전부 본다 — 앵커 락보다 앞에 일반 SELECT를 추가하면 이 보장이 깨진다.
        Long anchorId = reservationDao.findIdBySlotKeyForUpdate(waitingRequestDto.themeId(),
                        waitingRequestDto.timeId(), waitingRequestDto.date(), waitingRequestDto.storeId())
                .orElseThrow(() -> new BusinessRuleViolationException("예약이 존재하지 않아 대기가 불가능합니다."));
        Reservation reservation = reservationDao.findById(anchorId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 예약입니다."));

        // 큐 읽기는 무잠금 스냅샷 — 늘리는 쪽은 앵커가 직렬화하고, 삭제로 인한 낡음은 안전한 방향(과잉 거절)으로만 틀린다.
        Waitings waitings = waitingDao.findQueueBySlot(reservation.getSlot());
        Waiting ranked = waitings.enqueue(member, reservation, LocalDateTime.now());
        try {
            return waitingDao.insert(ranked);
        } catch (DuplicateKeyException e) {
            throw new DuplicateEntityException("이미 대기 신청한 슬롯입니다.");
        }
    }

    public void delete(Long waitingId) {
        if (!waitingDao.delete(waitingId)) {
            throw new EntityNotFoundException("존재하지 않는 예약 대기입니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<Waiting> findAll() {
        return waitingDao.findAllQueues().stream()
                .flatMap(queue -> queue.getAll().stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Waiting> findAllByMemberId(Long memberId) {
        return waitingDao.findQueuesContainingMember(memberId).stream()
                .flatMap(queue -> queue.ofMember(memberId).stream())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Waiting> findAllByStoreId(Long storeId) {
        return waitingDao.findQueuesByStoreId(storeId).stream()
                .flatMap(queue -> queue.getAll().stream())
                .toList();
    }
}
