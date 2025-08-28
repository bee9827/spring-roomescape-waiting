package roomescape.controller.dto.request;

import lombok.Builder;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Builder
public record ReservationSearchCriteria(
        @RequestParam(required = false) Long memberId,
        @RequestParam(required = false) Long themeId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo
) {
}
