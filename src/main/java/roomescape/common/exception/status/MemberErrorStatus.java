package roomescape.common.exception.status;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum MemberErrorStatus implements ErrorStatus {
    NOT_FOUND(HttpStatus.NOT_FOUND,"MEMBER_001","해당 사용자가 존재하지 않습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT,"MEMBER_002","이미 등록된 Email 입니다."),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST,"MEMBER_003","비밀번호가 잘못됐습니다."),
    INVALID_NAME_LENGTH(HttpStatus.BAD_REQUEST,"MEMBER_004","이름의 길이가 맞지 않습니다."),

    ;
    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    MemberErrorStatus(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
