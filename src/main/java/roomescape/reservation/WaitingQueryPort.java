package roomescape.reservation;

import roomescape.common.vo.Slot;

/**
 * 예약 생성 시 "이 슬롯에 대기자가 있나"를 물어보기 위한 포트.
 * reservation이 필요로 하는 것을 reservation 쪽에서 선언한다(DIP) — 구현은 waiting이 제공한다.
 * 덕분에 reservation은 waiting 패키지를 import하지 않는다(사이클 제거).
 */
public interface WaitingQueryPort {

    boolean existsBySlotForUpdate(Slot slot);
}
