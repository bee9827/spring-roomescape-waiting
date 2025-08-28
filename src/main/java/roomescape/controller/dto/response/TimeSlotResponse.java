package roomescape.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import roomescape.service.dto.result.TimeSlotResult;

import java.time.LocalTime;

public record TimeSlotResponse(
        Long id,
        @JsonFormat(pattern = "HH:mm")
        LocalTime startAt
) {

    public static TimeSlotResponse from(TimeSlotResult timeSlotResult) {
        return new TimeSlotResponse(
                timeSlotResult.id(),
                timeSlotResult.startAt()
        );
    }
}
