package roomescape.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import roomescape.TestDataInitializer;
import roomescape.TestFixture;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.TimeSlotErrorStatus;
import roomescape.domain.Reservation;
import roomescape.repository.ReservationRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.TimeSlotCreateCommand;
import roomescape.service.dto.result.AvailableTimeSlotResult;
import roomescape.service.dto.result.TimeSlotResult;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class TimeSlotServiceTest {
    @Autowired
    private TimeSlotService timeSlotService;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private TestDataInitializer testDataInitializer;

    @BeforeEach
    void setUp() {
        testDataInitializer.setUp();
    }

    @ParameterizedTest
    @CsvSource({"1", "2", "3"})
    @DisplayName("findAvailable: 예약된 타임 슬롯은 booked = true 만들어, 슬롯 전체를 리턴 한다.")
    void findAvailable(Long themeId) {
        List<AvailableTimeSlotResult> availableTimeSlotResults = timeSlotService.findAvailable(themeId, TestFixture.DEFAULT_DATE);

        List<TimeSlotResult> bookedAvailableTIme = availableTimeSlotResults
                .stream()
                .filter(AvailableTimeSlotResult::booked)
                .map(AvailableTimeSlotResult::timeSlotResult)
                .toList();

        List<TimeSlotResult> bookedTimes = reservationRepository.findAll()
                .stream()
                .filter(reservation -> themeId.equals(reservation.getTheme().getId()) && TestFixture.DEFAULT_DATE.equals(reservation.getDate()))
                .map(Reservation::getTimeSlot)
                .map(TimeSlotResult::from)
                .toList();

        assertThat(availableTimeSlotResults)
                .isNotEmpty()
                .size().isEqualTo(timeSlotRepository.findAll().size());  //모든 타임을 리턴 해야 한다.

        assertThat(bookedAvailableTIme.size()).isEqualTo(bookedTimes.size());   //booked 가 예약 된것으로 제대로 매핑되었는지.
    }

    @Test
    void findAll() {
        List<TimeSlotResult> timeSlotResults = timeSlotService.findAll();
        List<TimeSlotCreateCommand> savedTimeSlotCreateCommand = TestFixture.getTimeSlotCreateCommands();

        assertThat(timeSlotResults).isNotEmpty()
                .size().isEqualTo(savedTimeSlotCreateCommand.size());
    }

    @Nested
    @DisplayName("save: ")
    class Save {
        @Test
        @DisplayName("저장에 성공 한다.")
        void save() {
            LocalTime startAt = LocalTime.of(20, 0);
            TimeSlotCreateCommand createCommand = TimeSlotCreateCommand.builder()
                    .startAt(startAt)
                    .build();

            TimeSlotResult savedTime = timeSlotService.save(createCommand);
            assertThat(savedTime.startAt()).isEqualTo(startAt);
        }

        @Test
        @DisplayName("DUPLICATE_예외: start_at이 중복될 경우 예외를 던진다.")
        void duplicateStartAt() {
            TimeSlotCreateCommand alreadySavedTimeSlot = TestFixture.getTimeSlotCreateCommands().getFirst();
            assertThatThrownBy(() -> timeSlotService.save(alreadySavedTimeSlot))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(TimeSlotErrorStatus.DUPLICATE.getMessage());
        }
    }

    @Nested
    @DisplayName("deleteById: ")
    class DeleteById {
        @Test
        @DisplayName("성공한다.")
        void deleteById() {
            roomescape.domain.TimeSlot timeSlot = roomescape.domain.TimeSlot.builder()
                    .startAt(LocalTime.of(21, 0))
                    .build();
            timeSlotRepository.save(timeSlot);

            timeSlotService.deleteById(timeSlot.getId());

            assertThat(reservationRepository.existsById(timeSlot.getId())).isFalse();
        }

        @Test
        @DisplayName("NOT_FOUND 예외 : 존재하지 않는 Id입력시 예외를 던진다.")
        void notFound() {
            assertThatThrownBy(() -> timeSlotService.deleteById(0L))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(TimeSlotErrorStatus.NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("RESERVATION_EXIST 예외: 해당 시간에 대한 예약이 존재할 경우 예외를 던진다.")
        void reservationExist() {
            roomescape.domain.TimeSlot timeSlot = timeSlotRepository.findAll().getFirst();

            Reservation reservation = Reservation.builder()
                    .timeSlot(timeSlot)
                    .date(TestFixture.DEFAULT_DATE)
                    .build();

            reservationRepository.save(reservation);

            assertThatThrownBy(() -> timeSlotService.deleteById(timeSlot.getId()))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(TimeSlotErrorStatus.RESERVATION_EXIST.getMessage());
        }
    }
}