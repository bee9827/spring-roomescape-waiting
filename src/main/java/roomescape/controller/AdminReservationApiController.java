package roomescape.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roomescape.controller.dto.request.ReservationCreateRequestForAdmin;
import roomescape.controller.dto.request.ReservationSearchCriteria;
import roomescape.service.ReservationService;
import roomescape.controller.dto.response.ReservationResponse;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(AdminReservationApiController.BASE_URL)
@RequiredArgsConstructor
public class AdminReservationApiController {
    public static final String BASE_URL = "admin/reservations";

    private final ReservationService reservationService;

    @PostMapping
    public ResponseEntity<ReservationResponse> createReservation(
            @RequestBody
            @Valid
            ReservationCreateRequestForAdmin request
    ) {
        ReservationResponse savedReservation = ReservationResponse.from(reservationService.save(request.toCommand()));
        URI uri = URI.create(BASE_URL + "/" + savedReservation.id());

        return ResponseEntity.created(uri).body(savedReservation);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> filterReservations(
            @ModelAttribute ReservationSearchCriteria reservationSearchCriteria    //@ModelAttribute
    ) {
        List<ReservationResponse> reservations = reservationService.searchByCriteria(reservationSearchCriteria)
                .stream()
                .map(ReservationResponse::from)
                .toList();
        return ResponseEntity.ok(reservations);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteReservation(
            @PathVariable
            Long id
    ) {
        reservationService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
