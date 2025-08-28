package roomescape.controller.dto.response;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import roomescape.domain.ReservationStatus;
import roomescape.service.dto.result.ReservationResult;

import java.time.LocalDate;
import java.time.LocalTime;

@Builder
public record ReservationResponse(
        Long id,
        String name,
        String themeName,
        @JsonFormat(pattern = DATE_PATTERN) LocalDate date,
        @JsonFormat(pattern = TIME_PATTERN) LocalTime time,
        String status
) {
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm";


    public static ReservationResponse from(ReservationResult reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.memberResult().name(),
                reservation.themeResult().name(),
                reservation.date(),
                reservation.timeSlotResult().startAt(),
                reservation.reservationStatus().getName()
        );
    }
}
