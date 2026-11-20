package com.studenttask.common.api;

import java.util.List;

public record ApiError(String code, String message, List<FieldError> details) {
}
