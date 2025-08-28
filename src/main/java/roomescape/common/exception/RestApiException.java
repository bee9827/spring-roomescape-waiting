package roomescape.common.exception;

import roomescape.common.exception.status.ErrorStatus;

public class RestApiException extends RuntimeException {
    private final ErrorStatus errorStatus;

    public RestApiException(ErrorStatus errorStatus) {
        super(errorStatus.getMessage());
        this.errorStatus = errorStatus;
    }

    public ErrorStatus getErrorStatus() {
        return errorStatus;
    }
}
