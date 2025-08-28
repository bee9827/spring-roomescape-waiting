package roomescape.controller.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import roomescape.service.dto.command.ReservationCreateCommand;

import java.time.LocalDate;

public record ReservationCreateRequestForAdmin(
        @NotNull(message = "날짜는 공백일 수 없습니다.")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotNull(message = "멤버 Id는 공백일 수 없습니다.")
        Long memberId,

        @NotNull(message = "테마 Id는 공백일 수 없습니다.")
        Long themeId,

        @NotNull(message = "시간 Id는 공백일 수 없습니다.")
        @JsonProperty("timeId")
        Long timeSlotId
) {
    public ReservationCreateCommand toCommand() {
        return ReservationCreateCommand.builder()
                .memberId(memberId)
                .date(date)
                .themeId(themeId)
                .timeSlotId(timeSlotId)
                .build();
    }
}
