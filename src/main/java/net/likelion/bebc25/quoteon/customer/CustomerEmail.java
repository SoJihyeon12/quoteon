package net.likelion.bebc25.quoteon.customer;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = CustomerEmailValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface CustomerEmail {

    String message() default "이메일 형식을 확인해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
