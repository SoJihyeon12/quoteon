package net.likelion.bebc25.quoteon.customer;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CustomerTypeValidator implements ConstraintValidator<CustomerType, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return CustomerTypeCodes.isAllowed(value);
    }
}
