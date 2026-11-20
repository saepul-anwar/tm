package com.studenttask.common.error;

import com.studenttask.common.api.FieldError;
import java.util.List;

public class AppException extends RuntimeException {

    private final ErrorCode code;
    private final List<FieldError> details;

    public AppException(ErrorCode code, String message) {
        this(code, message, null);
    }

    /** {@code details} (optional) names the offending request fields in the error body. */
    public AppException(ErrorCode code, String message, List<FieldError> details) {
        super(message);
        this.code = code;
        this.details = details;
    }

    public ErrorCode code() {
        return code;
    }

    public List<FieldError> details() {
        return details;
    }
}
