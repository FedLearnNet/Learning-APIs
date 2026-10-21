package bio.cosy.feddb.core.security.bff;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "flnet.bff")
public interface BffConfig {
    Optional<String> frontendUrl();

    Optional<String> authServerPublicUrl();
}
