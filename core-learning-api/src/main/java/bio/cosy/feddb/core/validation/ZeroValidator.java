package bio.cosy.feddb.core.validation;

import bio.cosy.feddb.core.validation.constraints.Zero;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ZeroValidator implements ConstraintValidator<Zero, Number> {

    @Override
    public void initialize(Zero constraintAnnotation) {
    }

    @Override
    public boolean isValid(Number value, ConstraintValidatorContext context) {
        // Überprüfe, ob der Wert null oder 0 ist
        return value == null || value.intValue() == 0;
    }
}
