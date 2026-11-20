package com.studenttask.auth.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 8 to 72 bytes in UTF-8 (BCrypt limit), at least one letter and one digit. */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Kata sandi harus 8-72 byte dan memuat minimal 1 huruf dan 1 angka.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
