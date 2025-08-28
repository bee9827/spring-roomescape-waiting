package roomescape.service.dto.command;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;
import roomescape.domain.TimeSlot;

import java.time.LocalTime;

@Builder
public record TimeSlotCreateCommand(
        @NotNull(message = "시작 시간은 공백일 수 없습니다.")
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime startAt
        ) {

        public TimeSlot toEntity() {
                return new TimeSlot(
                        startAt
                );
        }
}
