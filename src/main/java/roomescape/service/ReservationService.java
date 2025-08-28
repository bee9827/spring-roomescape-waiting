package roomescape.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ReservationErrorStatus;
import roomescape.controller.dto.request.ReservationSearchCriteria;
import roomescape.domain.*;
import roomescape.repository.MemberRepository;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.ReservationCreateCommand;
import roomescape.service.dto.result.ReservationResult;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ThemeRepository themeRepository;
    private final MemberRepository memberRepository;

    public ReservationResult save(ReservationCreateCommand reservationCreateCommand) {
        Reservation createRequest = createReservation(reservationCreateCommand);

        return ReservationResult.from(save(createRequest));
    }

    public List<ReservationResult> findAll() {
        return reservationRepository.findAll()
                .stream()
                .map(ReservationResult::from)
                .toList();
    }

    public void deleteById(Long id) {
        Reservation reservation = getById(id);
        reservationRepository.delete(reservation);
    }

    public List<ReservationResult> searchByCriteria(ReservationSearchCriteria reservationSearchCriteria) {
        return filter(reservationSearchCriteria)
                .stream()
                .map(ReservationResult::from)
                .toList();
    }

    public ReservationResult findById(Long id) {
        Reservation reservation = getById(id);
        return ReservationResult.from(reservation);
    }

    private Reservation createReservation(ReservationCreateCommand reservationCreateCommand) {
        Member member = memberRepository.findById(reservationCreateCommand.memberId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.MEMBER_NOT_FOUND));
        TimeSlot timeSlot = timeSlotRepository.findById(reservationCreateCommand.timeSlotId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.TIME_SLOT_NOT_FOUND));
        Theme theme = themeRepository.findById(reservationCreateCommand.themeId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.THEME_NOT_FOUND));

        Reservation createRequest = reservationCreateCommand.toEntity(member, theme, timeSlot);

        if (bookedReservationExists(createRequest))
            createRequest.updateStatus(ReservationStatus.WAITING);
        else
            createRequest.updateStatus(ReservationStatus.BOOKED);

        return createRequest;
    }

    private List<Reservation> filter(ReservationSearchCriteria filter) {
        return reservationRepository.filter(
                filter.memberId(),
                filter.themeId(),
                filter.dateFrom(),
                filter.dateTo()
        );
    }

    private Reservation save(Reservation reservation) {
        validate(reservation);

        return reservationRepository.save(reservation);
    }

    private boolean bookedReservationExists(Reservation createRequest) {
        return reservationRepository.existsByDateAndThemeAndTimeSlot(
                createRequest.getDate(), createRequest.getTheme(), createRequest.getTimeSlot());
    }

    private Reservation getById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.NOT_FOUND));
    }

    private void validate(Reservation reservation) {
        if (checkDuplicate(reservation))
            throw new RestApiException(ReservationErrorStatus.DUPLICATE);
    }

    private boolean checkDuplicate(Reservation createRequest) {
        return reservationRepository.isDuplicated(createRequest.getMember(), createRequest.getTheme(), createRequest.getDate(), createRequest.getTimeSlot());
    }
}
