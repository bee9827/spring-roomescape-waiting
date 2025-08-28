package roomescape.common.exception.status;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum AuthErrorStatus implements ErrorStatus {

    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "AUTH_001", "유효하지 않은 토큰 입니다."),
    PAYLOAD_BLANK(HttpStatus.BAD_REQUEST, "AUTH_002", "토큰은 null 이 될 수 없습니다."),
    TOKEN_NOT_IN_COOKIE(HttpStatus.NOT_FOUND, "AUTH_003", "저장된 토큰이 없습니다."),
    NOT_FOUND_COOKIE(HttpStatus.BAD_REQUEST, "AUTH_004", "쿠키가 존재하지 않습니다."),
    NOT_AUTHORIZED(HttpStatus.UNAUTHORIZED,"AUTH_005","권한이 없습니다."),
;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    AuthErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

}
