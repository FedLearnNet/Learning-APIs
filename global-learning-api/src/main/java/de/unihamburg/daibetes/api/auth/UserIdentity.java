package de.unihamburg.daibetes.api.auth;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

@ApplicationScoped
public class UserIdentity {
    @Inject
    SecurityIdentity identity;

    public String getKeycloakId() {
        return identity.getPrincipal().getName();
    }

    public String getKeycloakIdOrElse(String elseValue) {
        if (identity == null || identity.getPrincipal() == null) {
            return elseValue;
        }
        String id = getKeycloakId();
        if (StringUtils.isEmpty(id)) {
            return elseValue;
        }
        return id;
    }

    public Set<String> getRoles() {
        return identity.getRoles();
    }

}
