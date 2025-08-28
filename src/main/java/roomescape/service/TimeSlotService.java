package roomescape.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.TimeSlotErrorStatus;
import roomescape.domain.TimeSlot;
import roomescape.repository.ReservationRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.TimeSlotCreateCommand;
import roomescape.service.dto.result.AvailableTimeSlotResult;
import roomescape.service.dto.result.TimeSlotResult;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeSlotService {
    private final ReservationRepository reservationRepository;
    private final TimeSlotRepository timeSlotRepository;

    public TimeSlotResult save(TimeSlotCreateCommand createCommand) {
        return TimeSlotResult.from(save(createCommand.toEntity()));
    }

    public List<AvailableTimeSlotResult> findAvailable(Long themeId, LocalDate date) {
        return timeSlotRepository.findAvailable(themeId, date);
    }

    public List<TimeSlotResult> findAll() {
        return timeSlotRepository.findAll()
                .stream()
                .map(TimeSlotResult::from)
                .toList();
    }

    public void deleteById(Long id) {
        validateReservationExists(id);

        timeSlotRepository.delete(findById(id));
    }

    private TimeSlot findById(Long id) {
        return timeSlotRepository.findById(id)
                .orElseThrow(() -> new RestApiException(TimeSlotErrorStatus.NOT_FOUND));
    }

    private void validateReservationExists(Long id) {
        if (reservationRepository.existsByTimeSlotId(id))
            throw new RestApiException(TimeSlotErrorStatus.RESERVATION_EXIST);
    }

    private TimeSlot save(TimeSlot entity) {
        validateDuplicate(entity);
        return timeSlotRepository.save(entity);
    }

    private void validateDuplicate(TimeSlot timeSlot) {
        if(timeSlotRepository.existsByStartAt(timeSlot.getStartAt())){
            throw new RestApiException(TimeSlotErrorStatus.DUPLICATE);
        };
    }

}
