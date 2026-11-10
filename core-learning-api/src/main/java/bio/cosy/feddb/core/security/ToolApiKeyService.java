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
        return issue(scope, runId, scope == Scope.PIPELINE_RUN ? null : runId);
    }

    public synchronized String issue(Scope scope, Long runId, Long executionId) {
        if (scope == null || runId == null) {
            throw new IllegalArgumentException("Tool API-key scope and run ID are required");
        }
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String apiKey = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String fingerprint = fingerprint(apiKey);
        Target target = new Target(scope, runId);
        revokeFingerprint(activeKeys.put(target, fingerprint));
        grants.put(fingerprint, new Grant(target, executionId));
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
            revokeFingerprint(fingerprint(apiKey));
        }
    }

    public synchronized void revoke(Scope scope, Long runId) {
        if (scope == null || runId == null) {
            return;
        }
        revokeFingerprint(activeKeys.get(new Target(scope, runId)));
    }

    public synchronized void revokeExecution(Long executionId) {
        if (executionId == null) {
            return;
        }
        var owned = grants.entrySet().stream()
                .filter(entry -> executionId.equals(entry.getValue().executionId()))
                .map(Map.Entry::getKey).toList();
        owned.forEach(this::revokeFingerprint);
    }

    public synchronized boolean isExecutionOwned(Scope scope, Long runId) {
        Grant grant = grants.get(activeKeys.get(new Target(scope, runId)));
        return grant != null && grant.executionId() != null;
    }

    private void revokeFingerprint(String fingerprint) {
        Grant grant = grants.remove(fingerprint);
        if (grant != null) {
            activeKeys.remove(grant.target(), fingerprint);
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
