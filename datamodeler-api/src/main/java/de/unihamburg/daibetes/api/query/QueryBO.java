package de.unihamburg.daibetes.api.query;

import de.unihamburg.daibetes.api.ontology.OntologyDAO;
import de.unihamburg.daibetes.api.schema.SchemaBO;
import de.unihamburg.daibetes.api.schema.SchemaDAO;
import de.unihamburg.daibetes.api.schema.SchemaSubscriptions;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class QueryBO {

    private static final Logger LOG = Logger.getLogger(QueryBO.class);

    @Inject
    OntologyDAO ontologyDAO;

    @Inject
    SchemaBO schemaBO;

    @Inject
    SchemaDAO schemaDAO;

    public Uni<List<QueryConfigDTO>> getQueryableNodes(List<UUID> ontologyIds) {
        List<UUID> sanitizedOntologyIds = ontologyIds == null
                ? List.of()
                : ontologyIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        boolean filterOntologyEmpty = sanitizedOntologyIds.isEmpty();

        Uni<List<SchemaSubscriptions>> schemasUni = filterOntologyEmpty
                ? schemaBO.getAllSubscriptions()
                : ontologyDAO.getAllRelatedSchema(sanitizedOntologyIds).collect().asList();

        return schemasUni
                .map(items -> items == null ? List.<SchemaSubscriptions>of() : items)
                .onFailure().invoke(ex -> LOG.error("Failed to load queryable schema subscriptions", ex))
                .onFailure().transform(ex -> new IllegalStateException("Failed to load queryable nodes", ex))
                .onItem().transformToMulti(list -> Multi.createFrom().iterable(list))
                .onItem().transformToUniAndMerge(this::buildQueryConfigSafely)
                .collect().asList()
                .map(this::mergeQueryConfigs);
    }

    private List<QueryConfigDTO> mergeQueryConfigs(List<QueryConfigDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<String, QueryConfigDTO> merged = new LinkedHashMap<>();

        for (QueryConfigDTO dto : items) {
            if (!isMergeable(dto)) {
                continue;
            }

            ensureMutableSchemaIds(dto);
            String key = dto.getOntology().getId() + "::" + dto.getDataType().getId();

            QueryConfigDTO existing = merged.get(key);
            if (existing == null) {
                merged.put(key, dto);
            } else {
                ensureMutableSchemaIds(existing);
                existing.addAnotherConfig(dto);
            }
        }

        List<QueryConfigDTO> result = new ArrayList<>(merged.values());
        result.sort((a, b) -> Integer.compare(b.getSubscriptionCount(), a.getSubscriptionCount()));
        return result;
    }

    private Uni<QueryConfigDTO> buildQueryConfigSafely(SchemaSubscriptions subscription) {
        if (subscription == null) {
            LOG.warn("Skipping null schema subscription");
            return Uni.createFrom().nullItem();
        }

        String schemaId = subscription.getSchemaId();
        if (schemaId == null || schemaId.isBlank()) {
            LOG.warnf("Skipping schema subscription with empty schemaId: %s", subscription);
            return Uni.createFrom().nullItem();
        }

        UUID schemaUuid;
        try {
            schemaUuid = UUID.fromString(schemaId);
        } catch (IllegalArgumentException ex) {
            LOG.warnf(ex, "Skipping schema subscription with invalid schemaId %s", schemaId);
            return Uni.createFrom().nullItem();
        }

        return schemaDAO.findDetail(schemaUuid)
                .map(detail -> {
                    if (detail == null) {
                        LOG.warnf("Skipping schema %s because no detail could be loaded", schemaId);
                        return null;
                    }

                    QueryConfigDTO dto = new QueryConfigDTO(subscription, detail);
                    ensureMutableSchemaIds(dto);
                    return dto;
                })
                .onFailure().invoke(ex -> LOG.warnf(ex, "Skipping schema %s while building queryable nodes", schemaId))
                .onFailure().recoverWithItem(() -> null);
    }

    private boolean isMergeable(QueryConfigDTO dto) {
        if (dto == null) {
            return false;
        }
        if (dto.getOntology() == null || dto.getOntology().getId() == null) {
            LOG.warnf("Skipping query config without ontology: %s", dto);
            return false;
        }
        if (dto.getDataType() == null || dto.getDataType().getId() == null) {
            LOG.warnf("Skipping query config without data type: %s", dto);
            return false;
        }
        return true;
    }

    private void ensureMutableSchemaIds(QueryConfigDTO dto) {
        if (dto == null) {
            return;
        }

        List<String> schemaIds = dto.getSchemaIds();
        if (schemaIds == null) {
            dto.setSchemaIds(new ArrayList<>());
            return;
        }

        if (!(schemaIds instanceof ArrayList)) {
            dto.setSchemaIds(new ArrayList<>(schemaIds));
        }
    }
}
