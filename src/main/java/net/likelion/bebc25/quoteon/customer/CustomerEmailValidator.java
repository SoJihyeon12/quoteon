package net.likelion.bebc25.quoteon.customer;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.hibernate.validator.internal.constraintvalidators.bv.EmailValidator;

import java.lang.annotation.Annotation;

public class CustomerEmailValidator implements ConstraintValidator<CustomerEmail, String> {

    static final int MAX_LENGTH = 255;

    private final EmailValidator emailValidator = new EmailValidator();

    @Override
    @SuppressWarnings("unchecked")
    public void initialize(CustomerEmail constraintAnnotation) {
        emailValidator.initialize(new Email() {
            @Override
            public String message() {
                return "이메일 형식을 확인해 주세요.";
            }

            @Override
            public Class<?>[] groups() {
                return new Class<?>[0];
            }

            @Override
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public String regexp() {
                return ".*";
            }

            @Override
            public Pattern.Flag[] flags() {
                return new Pattern.Flag[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Email.class;
            }
        });
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        if (value.length() > MAX_LENGTH) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("이메일은 255자 이하로 입력해 주세요.")
                    .addConstraintViolation();
            return false;
        }
        return emailValidator.isValid(value, context);
    }
}
