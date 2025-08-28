package roomescape.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import roomescape.service.dto.result.AvailableTimeSlotResult;

import java.time.LocalTime;

@Builder
public record AvailableTimeResponse(
        Long timeId,
        @JsonFormat(pattern = "HH:mm")
        LocalTime startAt,
        Boolean booked
) {
    public static AvailableTimeResponse from(AvailableTimeSlotResult availableTimeSlotResult) {
        return AvailableTimeResponse.builder()
                .timeId(availableTimeSlotResult.timeSlotResult().id())
                .startAt(availableTimeSlotResult.timeSlotResult().startAt())
                .booked(availableTimeSlotResult.booked())
                .build();
    }
}

