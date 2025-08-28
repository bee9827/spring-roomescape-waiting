package roomescape.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import roomescape.TestDataInitializer;
import roomescape.TestFixture;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ReservationErrorStatus;
import roomescape.controller.dto.request.ReservationSearchCriteria;
import roomescape.domain.Reservation;
import roomescape.domain.ReservationStatus;
import roomescape.repository.MemberRepository;
import roomescape.repository.ReservationRepository;
import roomescape.repository.ThemeRepository;
import roomescape.repository.TimeSlotRepository;
import roomescape.service.dto.command.ReservationCreateCommand;
import roomescape.service.dto.result.ReservationResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@Transactional
class ReservationServiceTest {
    @Autowired
    private ReservationService reservationService;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ThemeRepository themeRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private TestDataInitializer testDataInitializer;

    @BeforeEach
    public void setUp() {
        testDataInitializer.setUp();
    }

    @Nested
    @DisplayName("필터링 검색")
    class SearchByCriteria {

        @Test
        @DisplayName("조건이 없을때 모든 목록을 반환 한다.")
        void NoCriteria() {
            //when
            ReservationSearchCriteria reservationSearchCriteria = ReservationSearchCriteria.builder()
                    .build();   // 검색 조건이 없을때
            List<ReservationResult> reservationResults = reservationService.searchByCriteria(reservationSearchCriteria);

            assertAll(
                    () -> assertThat(reservationResults).hasSize(reservationService.findAll().size())
            );
        }

        @Test
        @DisplayName("조건이 있을경우 필터링하여 목록을 반환한다.")
        void filter() {
            ReservationSearchCriteria reservationSearchCriteria = ReservationSearchCriteria.builder()
                    .themeId(1L)
                    .build();   // 검색 조건이 없을때
            List<ReservationResult> reservationResults = reservationService.searchByCriteria(reservationSearchCriteria);

            List<ReservationResult> expectedResults = reservationService.findAll()
                    .stream()
                    .filter(resp -> resp.themeResult().id().equals(1L))
                    .toList();


            assertAll(
                    () -> assertThat(reservationResults).hasSize(expectedResults.size())
            );

        }
    }


    @Nested
    @DisplayName("save: ")
    class Save {
        ReservationCreateCommand reservationCreateCommand = ReservationCreateCommand.builder()
                .date(TestFixture.DEFAULT_DATE.plusDays(1))
                .memberId(1L)
                .themeId(1L)
                .timeSlotId(1L)
                .build();

        @Test
        @DisplayName("i) 저장된 예약이 없다면 Status를 Booked로 만들어 저장한다.")
        void save() {
            ReservationResult saved = reservationService.save(reservationCreateCommand);

            assertThat(saved.date()).isEqualTo(reservationCreateCommand.date());
            assertThat(saved.reservationStatus()).isEqualTo(ReservationStatus.BOOKED);
        }

        @Test
        @DisplayName("ii) 저장된 예약이 있다면 Status를 Waiting으로 만들어 저장한다.")
        void saveIfExists() {
            ReservationCreateCommand otherMemeberRreservationCreateCommand = ReservationCreateCommand.builder()
                    .date(TestFixture.DEFAULT_DATE.plusDays(1))
                    .memberId(2L)
                    .themeId(1L)
                    .timeSlotId(1L)
                    .build();
            reservationService.save(reservationCreateCommand);
            ReservationResult saved =  reservationService.save(otherMemeberRreservationCreateCommand);


            assertThat(saved.date()).isEqualTo(reservationCreateCommand.date());
            assertThat(saved.reservationStatus()).isEqualTo(ReservationStatus.WAITING);
        }

        @Test
        @DisplayName("예외: 저장된 Time이 없다면 예외를 던진다.")
        void timeSlotNotFound() {
            timeSlotRepository.deleteAll();

            assertThatThrownBy(() -> reservationService.save(reservationCreateCommand))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ReservationErrorStatus.TIME_SLOT_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("예외: 저장된 Theme이 없다면 예외를 던진다.")
        void themeNotFound() {
            themeRepository.deleteAll();

            assertThatThrownBy(() -> reservationService.save(reservationCreateCommand))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ReservationErrorStatus.THEME_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("예외: 저장된 Member가 없다면 예외를 던진다.")
        void memberNotFound() {
            memberRepository.deleteAll();

            assertThatThrownBy(() -> reservationService.save(reservationCreateCommand))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ReservationErrorStatus.MEMBER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("예외: 중복 이면 예외를 발생 시킨다.")
        void validate() {
            reservationService.save(reservationCreateCommand);

            assertThatThrownBy(() -> reservationService.save(reservationCreateCommand))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ReservationErrorStatus.DUPLICATE.getMessage());
        }
    }

    @Nested
    @DisplayName("findAll: ")
    class FindAll {
        @Test
        @DisplayName("성공하면 ReservtaionResult를 반환한다.")
        public void success() {
            List<ReservationResult> reservationResults = reservationService.findAll();

            List<Reservation> saved = reservationRepository.findAll();

            assertThat(reservationResults).hasSize(saved.size());
        }

    }

    @Nested
    @DisplayName("삭제")
    class deleteById {
        @Test
        @DisplayName("성공한다")
        void success() {
            Reservation reservation = reservationRepository.findAll().getFirst();
            reservationService.deleteById(reservation.getId());

            assertThat(reservationRepository.existsById(reservation.getId()))
                    .isFalse();
        }

        @Test
        @DisplayName("예외: 없는 아이디 라면 예외를 던진다.")
        void notFoundException() {
            assertThatThrownBy(() -> reservationService.deleteById(0L))
                    .isInstanceOf(RestApiException.class)
                    .hasMessage(ReservationErrorStatus.NOT_FOUND.getMessage());
        }
    }
}