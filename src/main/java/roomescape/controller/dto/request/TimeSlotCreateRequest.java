package roomescape.controller.dto.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import roomescape.service.dto.command.TimeSlotCreateCommand;

import java.time.LocalTime;

public record TimeSlotCreateRequest(
        @NotNull(message = "시작 시간은 공백일 수 없습니다.")
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime startAt
) {
        public TimeSlotCreateCommand toCommand(){
                return TimeSlotCreateCommand.builder()
                        .startAt(startAt)
                        .build();
        }
}
