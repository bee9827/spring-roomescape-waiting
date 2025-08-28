package roomescape.service.dto.result;

import java.time.LocalDate;
import lombok.Builder;
import roomescape.domain.Member;
import roomescape.domain.Reservation;
import roomescape.domain.ReservationStatus;

@Builder
public record ReservationResult(
        Long id,
        MemberResult memberResult,
        ThemeResult themeResult,
        TimeSlotResult timeSlotResult,
        LocalDate date,
        ReservationStatus reservationStatus
) {
    public static ReservationResult fromBookedReservation(Reservation reservation) {
        return ReservationResult.builder()
                .id(reservation.getId())
                .memberResult(MemberResult.from(reservation.getMember()))
                .themeResult(ThemeResult.from(reservation.getTheme()))
                .timeSlotResult(TimeSlotResult.from(reservation.getTimeSlot()))
                .date(reservation.getDate())
                .reservationStatus(ReservationStatus.BOOKED)
                .build();
    }

    public static ReservationResult fromWaitingReservation(Reservation reservation, Member member) {
        return ReservationResult.builder()
                .id(reservation.getId())
                .memberResult(MemberResult.from(member))
                .themeResult(ThemeResult.from(reservation.getTheme()))
                .timeSlotResult(TimeSlotResult.from(reservation.getTimeSlot()))
                .date(reservation.getDate())
                .reservationStatus(ReservationStatus.WAITING)
                .build();
    }

}
