package bio.cosy.feddb.core.security;

import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.quarkus.vertx.http.runtime.security.HttpAuthenticationMechanism;
import io.quarkus.vertx.http.runtime.security.HttpCredentialTransport;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.security.Principal;

/** Authenticates tool callbacks from the opaque API key header. */
@ApplicationScoped
public class ToolApiKeyAuthenticationMechanism implements HttpAuthenticationMechanism {

    public static final String ROLE = "tool-api-key";
    public static final String SCOPE_ATTRIBUTE = "tool-api-key.scope";
    public static final String RUN_ID_ATTRIBUTE = "tool-api-key.run-id";
    private static final String SCHEME = "tool-api-key";

    @Inject
    ToolApiKeyService apiKeys;

    @Override
    public Uni<SecurityIdentity> authenticate(RoutingContext context, IdentityProviderManager identityProviderManager) {
        String apiKey = context.request().getHeader(ToolApiKeyService.HEADER_NAME);
        if (apiKey == null || apiKey.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return Uni.createFrom().item(apiKeys.resolve(apiKey)
                .map(this::createIdentity)
                .orElse(null));
    }

    @Override
    public Uni<ChallengeData> getChallenge(RoutingContext context) {
        return Uni.createFrom().item(new ChallengeData(401));
    }

    @Override
    public Uni<HttpCredentialTransport> getCredentialTransport(RoutingContext context) {
        return Uni.createFrom().item(new HttpCredentialTransport(
                HttpCredentialTransport.Type.OTHER_HEADER, ToolApiKeyService.HEADER_NAME, SCHEME));
    }

    private SecurityIdentity createIdentity(ApiKeyIdentity apiKeyIdentity) {
        Principal principal = () -> "tool-api-key:" + apiKeyIdentity.scope() + ":" + apiKeyIdentity.runId();
        return QuarkusSecurityIdentity.builder()
                .setPrincipal(principal)
                .addRole(ROLE)
                .addAttribute(SCOPE_ATTRIBUTE, apiKeyIdentity.scope())
                .addAttribute(RUN_ID_ATTRIBUTE, apiKeyIdentity.runId())
                .build();
    }
}
