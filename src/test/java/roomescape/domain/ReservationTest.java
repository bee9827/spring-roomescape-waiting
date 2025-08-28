package roomescape.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ReservationErrorStatus;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservationTest {

    @Test
    @DisplayName("PAST 예외: 지난 시간에 대한 Reservation 생성은 불가능 하다.")
    public void presentOrFuture() {
        TimeSlot timeSlot = new TimeSlot(LocalTime.now());
        Member member = Member.builder()
                .name("테스트")
                .password("1007")
                .email("ehfrhfo9494@naver.com")
                .role(Role.USER)
                .build();

        assertThatThrownBy(
                () -> Reservation.builder()
                        .member(member)
                        .date(LocalDate.now())
                        .timeSlot(timeSlot)
                        .build())
                .isInstanceOf(RestApiException.class)
                .hasMessage(ReservationErrorStatus.INVALID_DATE_TIME.getMessage());
    }
}