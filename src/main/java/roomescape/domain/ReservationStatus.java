package roomescape.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ReservationStatus {
    BOOKED("예약"),
    WAITING("대기"),
    CANCELED("취소"),
    DENIED("거부");

    private final String name;
}
