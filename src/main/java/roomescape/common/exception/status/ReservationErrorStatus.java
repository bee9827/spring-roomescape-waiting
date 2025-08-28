package roomescape.common.exception.status;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ReservationErrorStatus implements ErrorStatus {
    NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_001", "요청된 예약을 찾을 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT, "RESERVATION_002", "중복 예약은 불가능 합니다."),
    INVALID_DATE_TIME(HttpStatus.BAD_REQUEST, "RESERVATION_003", "지나간 날짜와 시간에 대한 예약 생성은 불가능 합니다."),
    TIME_SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_004", "요청된 시간을 찾을 수 없어 예약이 불가능 합니다."),
    THEME_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_005", "요청된 테마를 찾을 수 없어 예약이 불가능 합니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "RESERVATION_006", "요청된 사용자를 찾을 수 없어 예약이 불가능 합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;


    ReservationErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

}
