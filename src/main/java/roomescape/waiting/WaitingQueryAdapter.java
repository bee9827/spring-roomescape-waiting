package roomescape.waiting;

import org.springframework.stereotype.Component;
import roomescape.common.vo.Slot;
import roomescape.reservation.WaitingQueryPort;

/**
 * reservation이 정의한 {@link WaitingQueryPort}를 waiting이 구현한다 → import 화살표가 waiting→reservation.
 * 실제 조회는 {@link WaitingDao}에 위임한다(어댑터는 얇게).
 */
@Component
public class WaitingQueryAdapter implements WaitingQueryPort {

    private final WaitingDao waitingDao;

    public WaitingQueryAdapter(WaitingDao waitingDao) {
        this.waitingDao = waitingDao;
    }

    @Override
    public boolean existsBySlotForUpdate(Slot slot) {
        return waitingDao.existsBySlotForUpdate(slot);
    }
}
