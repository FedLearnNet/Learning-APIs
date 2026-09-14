package bio.cosy.feddb.core.validation.constraints;

import bio.cosy.feddb.core.validation.ZeroValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Constraint(validatedBy = ZeroValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Zero {

    String message() default "must be zero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
