package roomescape.service.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;
import roomescape.domain.Member;
import roomescape.domain.Reservation;
import roomescape.domain.TimeSlot;
import roomescape.domain.Theme;

import java.time.LocalDate;

@Builder
public record ReservationCreateCommand(
        @NotBlank(message = "멤버 Id는 공백일 수 없습니다.")
        Long memberId,

        @NotNull(message = "날짜는 공백일 수 없습니다.")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotNull(message = "테마 Id는 공백일 수 없습니다.")
        Long themeId,

        @NotNull(message = "시간 Id는 공백일 수 없습니다.")
        @JsonProperty("timeId")
        Long timeSlotId
) {
    public Reservation toEntity(Member member, Theme theme, TimeSlot timeSlot) {
        return Reservation.builder()
                .member(member)
                .date(date)
                .theme(theme)
                .timeSlot(timeSlot)
                .build();
    }
}
