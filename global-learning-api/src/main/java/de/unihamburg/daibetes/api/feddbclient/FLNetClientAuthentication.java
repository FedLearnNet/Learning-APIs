package de.unihamburg.daibetes.api.feddbclient;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FLNetClientAuthentication {

    @ConfigProperty(name = "flnet.feddb-client.auth.enable", defaultValue = "true")
    boolean authEnabled;

    @Inject
    SecurityIdentity identity;

    public boolean isEnabled() {
        return authEnabled;
    }

    public boolean isAuthorized() {
        return !authEnabled || !identity.isAnonymous();
    }
}
