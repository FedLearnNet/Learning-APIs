package bio.cosy.feddb.core.validation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.util.Set;

@ApplicationScoped
public class ValidationHelper {

    @Inject
    Validator validator;

    public static void requireNotNull(Object value, String name) {
        if (value == null) {
            throw new ConstraintViolationException(name + " must not be null", Set.of());
        }
    }

    public void requireValid(Object bean, String name) {
        requireNotNull(bean, name);
        Set<ConstraintViolation<Object>> violations = validator.validate(bean);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
