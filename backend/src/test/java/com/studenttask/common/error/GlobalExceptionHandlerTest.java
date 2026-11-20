package com.studenttask.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studenttask.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    record Dto(String email) {
    }

    @Test
    void validation_failure_maps_to_400_with_field_details() throws Exception {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Dto("x"), "dto");
        binding.rejectValue("email", "Email", "must be a valid email");
        MethodParameter param = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("validation_failure_maps_to_400_with_field_details"), -1);

        ResponseEntity<ApiResponse<Void>> res =
                handler.handleValidation(new MethodArgumentNotValidException(param, binding));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ApiResponse<Void> body = res.getBody();
        assertThat(body.success()).isFalse();
        assertThat(body.error().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.error().details()).hasSize(1);
        assertThat(body.error().details().get(0).field()).isEqualTo("email");
        assertThat(body.error().details().get(0).message()).isEqualTo("must be a valid email");
    }

    @Test
    void unknown_exception_maps_to_500_without_stacktrace_in_body() throws Exception {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleUnknown(new IllegalStateException("secret-internal-detail"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        String json = new ObjectMapper().writeValueAsString(res.getBody());
        assertThat(json).doesNotContain("secret-internal-detail")
                .doesNotContain("IllegalStateException")
                .doesNotContain("at com.studenttask")
                .doesNotContain("stackTrace");
        assertThat(res.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void app_exception_maps_to_its_code_status() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleApp(new AppException(ErrorCode.NOT_FOUND, "Task not found"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().error().code()).isEqualTo("NOT_FOUND");
        assertThat(res.getBody().error().message()).isEqualTo("Task not found");
    }

    @Test
    void every_error_code_has_the_documented_status() {
        assertThat(ErrorCode.VALIDATION_ERROR.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHENTICATED.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.status()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.NOT_FOUND.status()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.CONFLICT.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.RATE_LIMITED.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.TELEGRAM_UNAVAILABLE.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unsupported_method_maps_to_405() {
        ResponseEntity<ApiResponse<Void>> res =
                handler.handleMethodNotSupported(new HttpRequestMethodNotSupportedException("GET"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unsupported_media_type_maps_to_415() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, java.util.List.of(MediaType.APPLICATION_JSON), HttpMethod.POST));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(res.getBody().error().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void not_acceptable_maps_to_406() {
        ResponseEntity<ApiResponse<Void>> res = handler.handleMediaTypeNotAcceptable(
                new HttpMediaTypeNotAcceptableException(java.util.List.of(MediaType.APPLICATION_JSON)));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(res.getBody().error().code()).isEqualTo("NOT_ACCEPTABLE");
    }

    @Test
    void new_error_codes_have_expected_status() {
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.status()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(ErrorCode.UNSUPPORTED_MEDIA_TYPE.status()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(ErrorCode.NOT_ACCEPTABLE.status()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }
}
