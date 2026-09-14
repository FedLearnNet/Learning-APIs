package bio.cosy.feddb.local.api.search;

import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectBO;
import bio.cosy.feddb.local.api.learning.project.run.message.FederatedLearningExperimentStepMessageBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.api.learning.request.metrics.RequestRunMetricsBO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.api.statistics.request.RequestDataStatisticsBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class SearchBO {

    private static final int DEFAULT_LIMIT = 20;

    @Inject
    QueryBO queryBO;

    @Inject
    CohortBO cohortBO;

    @Inject
    PatientBO patientBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    FederatedLearningRequestBO federatedLearningRequestBO;

    @Inject
    RequestDataStatisticsBO requestDataStatisticsBO;

    @Inject
    RequestRunMetricsBO requestRunMetricsBO;

    @Inject
    FederatedLearningProjectBO federatedLearningProjectBO;

    @Inject
    FederatedLearningExperimentStepMessageBO federatedLearningExperimentStepMessageBO;

    public List<SearchResultDTO<?>> search(String query, Integer limit, String keycloakId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Set<Long> cohortIds = cohortBO.getAll(keycloakId).stream()
                .map(CohortDTO::getId)
                .collect(java.util.stream.Collectors.toSet());

        List<SearchResultDTO<?>> results = new ArrayList<>();
        results.addAll(queryBO.search(query, keycloakId));
        results.addAll(cohortBO.search(query, keycloakId));
        results.addAll(connectorBO.search(query, keycloakId));
        results.addAll(patientBO.search(query, keycloakId));
        results.addAll(schemaNodeBO.search(query, cohortIds));
        results.addAll(federatedLearningRequestBO.search(query, keycloakId));
        results.addAll(requestDataStatisticsBO.search(query, keycloakId));
        results.addAll(requestRunMetricsBO.search(query, keycloakId));
        results.addAll(federatedLearningProjectBO.search(query, keycloakId));
        results.addAll(federatedLearningExperimentStepMessageBO.search(query, keycloakId));

        return results.stream()
                .sorted(Comparator.comparingInt(SearchResultDTO<?>::getScore).reversed()
                        .thenComparing(SearchResultDTO::getTitle, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(resolveLimit(limit))
                .toList();
    }

    private int resolveLimit(Integer limit) {
        return limit == null || limit <= 0 ? DEFAULT_LIMIT : limit;
    }
}
