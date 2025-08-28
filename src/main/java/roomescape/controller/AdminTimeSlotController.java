package roomescape.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roomescape.controller.dto.request.TimeSlotCreateRequest;
import roomescape.controller.dto.response.TimeSlotResponse;
import roomescape.service.TimeSlotService;

import java.net.URI;
import java.util.List;

import static roomescape.controller.AdminTimeSlotController.BASE_URL;

@RestController
@RequestMapping(BASE_URL)
@RequiredArgsConstructor
public class AdminTimeSlotController {
    public final static String BASE_URL = "admin/times";
    private final TimeSlotService timeSlotService;

    @PostMapping
    public ResponseEntity<TimeSlotResponse> create(
            @RequestBody
            @Valid
            TimeSlotCreateRequest timeSlotCreateRequest) {
        TimeSlotResponse responseDto = TimeSlotResponse.from(timeSlotService.save(timeSlotCreateRequest.toCommand()));
        return ResponseEntity.created(URI.create("/times")).body(responseDto);
    }

    @GetMapping
    public ResponseEntity<List<TimeSlotResponse>> findAll() {
        List<TimeSlotResponse> timeSlotResponses = timeSlotService.findAll()
                .stream()
                .map(TimeSlotResponse::from)
                .toList();
        return ResponseEntity.ok(timeSlotResponses);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        //잘못된 아이디 전달 했을 경우 예외 처리 필요.
        timeSlotService.deleteById(id);

        return ResponseEntity.noContent().build();
    }
}
