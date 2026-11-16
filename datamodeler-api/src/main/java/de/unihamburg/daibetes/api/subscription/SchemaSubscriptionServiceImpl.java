package de.unihamburg.daibetes.api.subscription;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import bio.cosy.feddb.core.api.datamodler.subscription.SchemaSubscriptionService;
import de.unihamburg.daibetes.api.schema.SchemaBO;
import de.unihamburg.daibetes.api.schema.SchemaDAO;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@ApplicationScoped
public class SchemaSubscriptionServiceImpl implements SchemaSubscriptionService {

    @Inject
    SchemaBO schemaBO;

    @Inject
    SchemaDAO schemaDAO;

    @Inject
    SecurityIdentity identity;

    @Override
    public Uni<SchemaStructureDTO> subscribe(UUID id) {
        return schemaDAO.subscribe(id, subscriberId())
                .onItem().transformToUni(success -> {
                    if (!success) {
                        return Uni.createFrom().failure(new RuntimeException("Subscription failed for schema " + id));
                    }
                    return schemaBO.getSubStructure(id);
                });
    }

    @Override
    public Uni<Response> unsubscribe(UUID id) {
        return schemaDAO.unsubscribe(id, subscriberId()).onItem().transform(resp -> Response.ok().build());
    }

    // anonymous but stable per client: hash of the token subject, never the username
    private String subscriberId() {
        String subject = identity.getPrincipal() instanceof JsonWebToken jwt && jwt.getSubject() != null
                ? jwt.getSubject()
                : identity.getPrincipal().getName();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(subject.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
