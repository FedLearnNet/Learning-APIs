package bio.cosy.feddb.core.security;

public record ApiKeyIdentity(Scope scope, Long runId) {
}
