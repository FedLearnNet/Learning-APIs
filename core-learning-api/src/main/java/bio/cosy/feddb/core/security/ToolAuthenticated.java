package bio.cosy.feddb.core.security;

import io.quarkus.security.Authenticated;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a callback that requires a run-scoped tool API key.
 */
@Authenticated
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ToolAuthenticated {

    Scope[] value();
}
