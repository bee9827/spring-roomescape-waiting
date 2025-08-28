package roomescape.common.exception.status;

import org.springframework.http.HttpStatus;

public interface ErrorStatus {
    public HttpStatus getHttpStatus();
    public String getMessage();
}
