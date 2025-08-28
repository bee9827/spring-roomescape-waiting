package roomescape.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ReservationErrorStatus;
import roomescape.controller.dto.request.ReservationSearchCriteria;
import roomescape.domain.Member;
import roomescape.domain.Reservation;
import roomescape.domain.Theme;
import roomescape.domain.TimeSlot;
import roomescape.repository.MemberRepository;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.ReservationCreateCommand;
import roomescape.service.dto.result.ReservationResult;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ThemeRepository themeRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public ReservationResult save(ReservationCreateCommand reservationCreateCommand) {
        Reservation createRequest = createReservation(reservationCreateCommand);
        if (checkDuplicated(createRequest)) {
            return waiting(reservationCreateCommand);
        }

        reservationRepository.save(createRequest);
        return ReservationResult.fromBookedReservation(createRequest);
    }

    // TODO: WaitingService에서 진행 ㅠㅠ
//    private ReservationResult waiting(ReservationCreateCommand reservationCreateCommand) {
//        Member member = memberRepository.findById(reservationCreateCommand.memberId())
//                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.MEMBER_NOT_FOUND));
//        TimeSlot timeSlot = timeSlotRepository.findById(reservationCreateCommand.timeSlotId())
//                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.TIME_SLOT_NOT_FOUND));
//        Theme theme = themeRepository.findById(reservationCreateCommand.themeId())
//                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.THEME_NOT_FOUND));
//
//        Reservation reservation = reservationRepository.findByThemeAndTimeSlotAndDate(theme, timeSlot,
//                reservationCreateCommand.date());
//        validateReservation(reservation);
//
//        reservation.waiting(member);
//        reservationRepository.save(reservation);
//        
//        return ReservationResult.fromWaitingReservation(reservation, member);
//    }

    public boolean checkDuplicated(Reservation reservation) {
        return reservationRepository.existsByDateAndThemeAndTimeSlot(reservation.getDate(), reservation.getTheme(),
                reservation.getTimeSlot());
    }

    private void validateReservation(Reservation reservation) {
        if (reservation == null) {
            throw new RestApiException(ReservationErrorStatus.NOT_FOUND);
        }
    }

    private void validateDuplicated(Reservation createRequest) {
        if (reservationRepository.isDuplicated(createRequest.getTheme(), createRequest.getDate(),
                createRequest.getTimeSlot())) {
            throw new RestApiException(ReservationErrorStatus.DUPLICATE);
        }
    }

    public List<ReservationResult> findAll() {
        return reservationRepository.findAll()
                .stream()
                .map(ReservationResult::fromBookedReservation)
                .toList();
    }

    @Transactional
    public void deleteById(Long id) {
        Reservation reservation = getById(id);
        reservationRepository.delete(reservation);
    }

    public List<ReservationResult> searchByCriteria(ReservationSearchCriteria reservationSearchCriteria) {
        return filter(reservationSearchCriteria)
                .stream()
                .map(ReservationResult::fromBookedReservation)
                .toList();
    }

    public ReservationResult findById(Long id) {
        Reservation reservation = getById(id);
        return ReservationResult.fromBookedReservation(reservation);
    }

    private Reservation createReservation(ReservationCreateCommand reservationCreateCommand) {
        Member member = memberRepository.findById(reservationCreateCommand.memberId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.MEMBER_NOT_FOUND));
        TimeSlot timeSlot = timeSlotRepository.findById(reservationCreateCommand.timeSlotId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.TIME_SLOT_NOT_FOUND));
        Theme theme = themeRepository.findById(reservationCreateCommand.themeId())
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.THEME_NOT_FOUND));

        return reservationCreateCommand.toEntity(member, theme, timeSlot);
    }

    private List<Reservation> filter(ReservationSearchCriteria filter) {
        return reservationRepository.filter(
                filter.memberId(),
                filter.themeId(),
                filter.dateFrom(),
                filter.dateTo()
        );
    }

    private Reservation getById(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new RestApiException(ReservationErrorStatus.NOT_FOUND));
    }
}
