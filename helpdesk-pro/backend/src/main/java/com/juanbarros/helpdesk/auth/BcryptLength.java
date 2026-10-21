package com.juanbarros.helpdesk.auth;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.charset.StandardCharsets;

/**
 * BCrypt solo usa los primeros 72 bytes de la contraseña e ignora el resto, así que una más larga
 * dejaría entrar con cualquier texto que empiece igual. Se cuentan bytes UTF-8, no caracteres:
 * la ñ o las letras con acento ocupan dos.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BcryptLength.Validator.class)
public @interface BcryptLength {

    int MAX_BYTES = 72;

    String message() default "La contraseña es demasiado larga (la ñ y las letras con acento ocupan el doble)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<BcryptLength, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
        }
    }
}
