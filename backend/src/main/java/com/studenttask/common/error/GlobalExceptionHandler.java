package com.studenttask.common.error;

import com.studenttask.common.api.ApiResponse;
import com.studenttask.common.api.FieldError;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final int MYSQL_NO_REFERENCED_ROW = 1452;
    private static final String GENERIC_ERROR = "Terjadi kesalahan pada server.";

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        return respond(ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldError(e.getField(), e.getDefaultMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, "Data yang dikirim tidak valid.", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraint(ConstraintViolationException ex) {
        List<FieldError> details = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return respond(ErrorCode.VALIDATION_ERROR, "Data yang dikirim tidak valid.", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return respond(ErrorCode.VALIDATION_ERROR, "Format permintaan tidak valid.", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(ErrorCode.VALIDATION_ERROR, "Parameter tidak valid.", null);
    }

    /** Concurrent write lost the optimistic-lock race (the version check in the service covers the sequential case). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return respond(ErrorCode.CONFLICT, "Data telah diubah di tempat lain. Muat ulang lalu coba lagi.", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, "Metode HTTP tidak didukung.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return respond(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Tipe konten tidak didukung.", null);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return respond(ErrorCode.NOT_ACCEPTABLE, "Format respons yang diminta tidak tersedia.", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return respond(ErrorCode.NOT_FOUND, "Sumber daya tidak ditemukan.", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return respond(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return respond(ErrorCode.FORBIDDEN, "Akses ditolak.", null);
    }

    /** Last resort: log the exception server-side only (type + trace), return a generic body. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        if (isMissingReferencedUser(ex)) {
            // A still-valid access token whose user row no longer exists: a write hit the users foreign key.
            log.warn("Write rejected: authenticated user no longer exists");
            return respond(ErrorCode.UNAUTHENTICATED, "Autentikasi diperlukan.", null);
        }
        log.error("Unhandled exception: {}", ex.getClass().getName(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, GENERIC_ERROR, null);
    }

    /** MySQL ER_NO_REFERENCED_ROW_2: every foreign key written from the API points at users(id). */
    private static boolean isMissingReferencedUser(Throwable ex) {
        for (Throwable cause = ex; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == MYSQL_NO_REFERENCED_ROW) {
                return true;
            }
        }
        return false;
    }

    private static ResponseEntity<ApiResponse<Void>> respond(
            ErrorCode code, String message, List<FieldError> details) {
        return ResponseEntity.status(code.status()).body(ApiResponse.error(code, message, details));
    }
}
