package roomescape.common.exception.status;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum TimeSlotErrorStatus implements ErrorStatus {
    NOT_FOUND(HttpStatus.NOT_FOUND, "TIME_001", "요청된 시간을 찾을 수 없습니다."),
    RESERVATION_EXIST(HttpStatus.CONFLICT, "TIME_002", "해당 시간 예약이 존재 하여 삭제할 수 없습니다."),
    DUPLICATE(HttpStatus.CONFLICT,"TIME_003","해당 시간이 이미 존재하여 추가 할 수 없습니다."),
    ;

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    TimeSlotErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
