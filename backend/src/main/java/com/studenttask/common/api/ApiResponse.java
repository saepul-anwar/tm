package com.studenttask.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.studenttask.common.error.ErrorCode;
import java.util.List;

/** Uniform response envelope: {success,data,meta} or {success:false,error:{code,message,details}}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data, ApiError error, PageMeta meta) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data, PageMeta meta) {
        return new ApiResponse<>(true, data, null, meta);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, List<FieldError> details) {
        return new ApiResponse<>(false, null, new ApiError(code.name(), message, details), null);
    }
}
