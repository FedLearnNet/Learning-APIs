package bio.cosy.feddb.core.security;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** Keeps one ephemeral, fingerprint-only credential for each active tool run. */
@ApplicationScoped
public class ToolApiKeyService {

    public static final String HEADER_NAME = "X-API-Key";
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Grant> grants = new HashMap<>();
    private final Map<Target, String> activeKeys = new HashMap<>();

    public synchronized String issue(Scope scope, Long runId) {
        if (scope == null || runId == null) {
            throw new IllegalArgumentException("Tool API-key scope and run ID are required");
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String fingerprint = fingerprint(apiKey);
        Target target = new Target(scope, runId);
        String previousFingerprint = activeKeys.put(target, fingerprint);
        if (previousFingerprint != null) {
            grants.remove(previousFingerprint);
        }
        grants.put(fingerprint, new Grant(target));
        return apiKey;
    }

    public boolean isAuthorized(String apiKey, Scope scope, Long runId) {
        return resolve(apiKey)
                .map(identity -> identity.scope() == scope && identity.runId().equals(runId))
                .orElse(false);
    }

    public synchronized Optional<ApiKeyIdentity> resolve(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        String fingerprint = fingerprint(apiKey);
        Grant grant = grants.get(fingerprint);
        if (grant == null) {
            return Optional.empty();
        }
        return Optional.of(new ApiKeyIdentity(grant.target().scope(), grant.target().runId()));
    }

    public synchronized void revoke(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            String fingerprint = fingerprint(apiKey);
            Grant grant = grants.remove(fingerprint);
            if (grant != null) {
                activeKeys.remove(grant.target(), fingerprint);
            }
        }
    }

    public synchronized void revoke(Scope scope, Long runId) {
        if (scope == null || runId == null) {
            return;
        }
        Target target = new Target(scope, runId);
        String fingerprint = activeKeys.remove(target);
        if (fingerprint != null) {
            grants.remove(fingerprint);
        }
    }

    private static String fingerprint(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

}
