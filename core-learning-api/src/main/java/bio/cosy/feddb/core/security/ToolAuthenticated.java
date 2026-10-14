package bio.cosy.feddb.core.security;

import io.quarkus.security.Authenticated;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that is called by a tool container with a run-scoped API key
 * ({@link ToolApiKeyService#HEADER_NAME}), never by the frontend.
 * <p>
 * IMPORTANT: this annotation does not enforce anything by itself. When you add it, also add
 *   - {@code @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)} on the same method/class, so
 *       Keycloak users are rejected, and
 *   - a rule in the module's {@code ToolEndpointSecurityPolicy}, which binds the key's scope and
 *       run id to the path (see {@link BaseToolEndpointSecurityPolicy}). Without it, API keys are
 *       refused on this endpoint.
 * {@code ToolAuthenticatedEndpointsTest} in each module fails if either is missing but an endpoint
 * is annotated with {@code @ToolAuthenticated}.
 */
@Authenticated
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface ToolAuthenticated {

    Scope[] value();
}
