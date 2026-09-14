package de.unihamburg.daibetes.api.subscription;

import bio.cosy.feddb.core.api.datamodler.subscription.SchemaSubscriptionService;
import de.unihamburg.daibetes.api.schema.SchemaBO;
import de.unihamburg.daibetes.api.schema.SchemaDAO;
import bio.cosy.feddb.core.api.datamodler.schema.SchemaStructureDTO;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@ApplicationScoped
public class SchemaSubscriptionServiceImpl implements SchemaSubscriptionService {

    @Inject
    SchemaBO schemaBO;

    @Inject
    SchemaDAO schemaDAO;

    @Override
    public Uni<SchemaStructureDTO> subscribe(UUID id) {
        String userId = "test-user"; // TODO: get from security context
        return schemaDAO.subscribe(id, userId)
                .onItem().transformToUni(success -> {
                    if (!success) {
                        return Uni.createFrom().failure(new RuntimeException("Subscription failed"));
                    }
                    return schemaBO.getSubStructure(id);
                });
    }

    @Override
    public Uni<Response> unsubscribe(UUID id) {
        String userId = "test-user"; // TODO: get from security context
        return schemaDAO.unsubscribe(id, userId).onItem().transform(resp -> Response.ok().build());
    }
}
