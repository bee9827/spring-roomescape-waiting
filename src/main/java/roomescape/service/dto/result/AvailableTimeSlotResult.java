package roomescape.service.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import roomescape.domain.TimeSlot;

@Builder
public record AvailableTimeSlotResult(
        @JsonProperty("time")
        TimeSlotResult timeSlotResult,
        Boolean booked
) {
    public AvailableTimeSlotResult(TimeSlot timeSlot, Boolean booked) {
        this(TimeSlotResult.from(timeSlot), booked);
    }
}
