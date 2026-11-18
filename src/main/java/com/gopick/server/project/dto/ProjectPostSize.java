package com.gopick.server.project.dto;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = ProjectPostSize.Validator.class)
public @interface ProjectPostSize {
    String message() default "모집글은 유효한 UTF-8 텍스트 65,535바이트 이하여야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ProjectPostSize, String> {
        private static final int MYSQL_TEXT_MAX_BYTES = 65_535;

        @Override
        public boolean isValid(String post, ConstraintValidatorContext context) {
            if (post == null) {
                return true;
            }
            if (post.length() > MYSQL_TEXT_MAX_BYTES) {
                return false;
            }
            try {
                return StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(post)).remaining()
                        <= MYSQL_TEXT_MAX_BYTES;
            } catch (CharacterCodingException malformedText) {
                return false;
            }
        }
    }
}
