package roomescape.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import roomescape.common.exception.RestApiException;
import roomescape.common.exception.status.ErrorStatus;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(RestApiException.class)
    public ResponseEntity<String> handleRestApiException(RestApiException e) {
        return ResponseEntity.status(e.getErrorStatus().getHttpStatus()).body(e.getErrorStatus().toString());
    }
}
