package roomescape.service.dto.result;

import lombok.Builder;

import java.time.LocalTime;

@Builder
public record TimeSlotResult(
        Long id,
        LocalTime startAt
) {
    public static TimeSlotResult from(roomescape.domain.TimeSlot timeSlot) {
        return TimeSlotResult.builder()
                .id(timeSlot.getId())
                .startAt(timeSlot.getStartAt())
                .build();
    }
}
