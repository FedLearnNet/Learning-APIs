package de.unihamburg.daibetes.api.query;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;


@ApplicationScoped
public class QueryServiceImpl implements QueryService {

    @Inject
    QueryBO queryBO;

    @Override
    public Uni<List<QueryConfigDTO>> getQueryableNodes(List<UUID> ontologyIds) {
        return queryBO.getQueryableNodes(ontologyIds);
    }
}
