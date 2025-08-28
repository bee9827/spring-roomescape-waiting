package roomescape.service.dto.result;

import lombok.Builder;
import roomescape.domain.Reservation;
import roomescape.domain.ReservationStatus;

import java.time.LocalDate;

@Builder
public record ReservationResult(
        Long id,
        MemberResult memberResult,
        ThemeResult themeResult,
        TimeSlotResult timeSlotResult,
        LocalDate date,
        ReservationStatus reservationStatus
) {
    public static ReservationResult from(Reservation reservation) {
        return ReservationResult.builder()
                .id(reservation.getId())
                .memberResult(MemberResult.from(reservation.getMember()))
                .themeResult(ThemeResult.from(reservation.getTheme()))
                .timeSlotResult(TimeSlotResult.from(reservation.getTimeSlot()))
                .date(reservation.getDate())
                .reservationStatus(reservation.getStatus())
                .build();
    }

}
