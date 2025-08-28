package roomescape.common.exception.status;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ThemeErrorStatus implements ErrorStatus {
    NOT_FOUND(HttpStatus.NOT_FOUND, "THEME_001", "요청된 테마를 찾을 수 없습니다."),
    RESERVATION_EXIST(HttpStatus.CONFLICT, "THEME_002", "해당 테마 예약이 존재 하여 삭제할 수 없습니다."),
    ;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ThemeErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
