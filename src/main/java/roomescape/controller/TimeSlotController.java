package roomescape.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import roomescape.controller.dto.response.AvailableTimeResponse;
import roomescape.controller.dto.response.TimeSlotResponse;
import roomescape.service.TimeSlotService;
import roomescape.service.dto.result.TimeSlotResult;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/times")
@RequiredArgsConstructor
public class TimeSlotController {
    private final TimeSlotService timeSlotService;

    @GetMapping("/available")
    public ResponseEntity<List<AvailableTimeResponse>> available(
            @RequestParam final Long themeId,
            @RequestParam final LocalDate date
    ) {
        List<AvailableTimeResponse> availableTimeResponses = timeSlotService.findAvailable(themeId, date)
                .stream()
                .map(AvailableTimeResponse::from)
                .toList();

        return ResponseEntity.ok(availableTimeResponses);
    }
}
