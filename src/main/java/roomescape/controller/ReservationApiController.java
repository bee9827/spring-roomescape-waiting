package roomescape.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import roomescape.common.resolver.AuthMember;
import roomescape.controller.dto.request.ReservationCreateRequestForMember;
import roomescape.controller.dto.request.ReservationSearchCriteria;
import roomescape.controller.dto.response.ReservationResponse;
import roomescape.service.AuthService;
import roomescape.service.ReservationService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping(ReservationApiController.BASE_URL)
@RequiredArgsConstructor
public class ReservationApiController {
    public static final String BASE_URL = "/reservations";
    private final ReservationService reservationService;
    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ReservationResponse> create(
            @RequestBody
            @Valid
            ReservationCreateRequestForMember requestDto,

            @AuthMember
            Long authMemberId
    ) {
        ReservationResponse reservationResponse = ReservationResponse.from(reservationService.save(requestDto.toCommand(authMemberId)));
        URI uri = URI.create("/reservations/" + authMemberId);

        return ResponseEntity.created(uri).body(reservationResponse);
    }

    @GetMapping
    public ResponseEntity<List<ReservationResponse>> getAll(@AuthMember Long authMemberId) {
        ReservationSearchCriteria reservationSearchCriteria = ReservationSearchCriteria.builder()
                .memberId(authMemberId)
                .build();

        List<ReservationResponse> reservationResponses = reservationService.searchByCriteria(reservationSearchCriteria)
                .stream()
                .map(ReservationResponse::from)
                .toList();

        return ResponseEntity.ok(reservationResponses);
    }

    @DeleteMapping("/{reservationId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long reservationId,
            @AuthMember Long authMemberId) {
        authService.validateOwner(reservationId, authMemberId);
        reservationService.deleteById(reservationId);

        return ResponseEntity.noContent().build();
    }
}
